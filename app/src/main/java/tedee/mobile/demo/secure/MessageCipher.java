package tedee.mobile.demo.secure;

import static tedee.mobile.demo.secure.SecureConnectionConstants.LEN_AUTH_TAG;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MessageCipher {

    private final Cipher cipher;
    private final SecretKey secretKey; // encryption/decryption key
    private final byte[] iv; // current iv
    private final byte[] ivCounterBase; // base counter in iv
    private final int mode; // mode of operation
    private int counter; // counter

    public static int getLength(byte[] message, int offset) {
        return ((message[offset] & 0xff) << 8) | (message[offset + 1] & 0xff);
    }

    public static void setLength(byte[] message, int offset, int len) {
        message[offset] = (byte) ((len >> 8) & 0xff);
        message[offset + 1] = (byte) (len & 0xff);
    }

    /**
     * Prepare encryption or decryption object.
     */
    public MessageCipher(SecretKey sharedSecret, byte[] label, byte[] data, int mode)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        // calculate key and iv
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(sharedSecret);
        mac.update(label);
        mac.update(data);
        byte[] secret = mac.doFinal();
        this.cipher = Cipher.getInstance("AES/GCM/NoPadding");
        this.secretKey = new SecretKeySpec(secret, 0, 16, "AES");
        this.iv = new byte[12];
        System.arraycopy(secret, 16, iv, 0, 12);
        this.ivCounterBase = new byte[2];
        this.ivCounterBase[0] = iv[10];
        this.ivCounterBase[1] = iv[11];
        this.counter = 0;
        this.mode = mode;
    }

    public MessageCipher(SecretKey sharedSecret, byte[] label, byte[] data, int mode, CipherData cipherData)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        // calculate key and iv
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(sharedSecret);
        mac.update(label);
        mac.update(data);
        byte[] secret = mac.doFinal();
        this.cipher = Cipher.getInstance("AES/GCM/NoPadding");
        this.secretKey = new SecretKeySpec(secret, 0, 16, "AES");
        this.iv = cipherData.getIv();
        this.ivCounterBase = cipherData.getIvCounterBase();
        this.counter = cipherData.getCounter();
        this.mode = mode;
    }

    /**
     * Transform (encrypt or decrypt) message.
     */
    public byte[] transform(byte[] message, int len)
            throws InvalidAlgorithmParameterException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException {
        if (counter > 0xffff) // counter overflow
        {
            throw new InvalidAlgorithmParameterException();
        }
        iv[10] = ivCounterBase[0]; // restore orginal iv
        iv[11] = ivCounterBase[1];
        iv[10] ^= (byte) ((counter >> 8) & 0xff); // add counter
        iv[11] ^= (byte) (counter & 0xff);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(LEN_AUTH_TAG * 8, iv, 0, 12);
        cipher.init(mode, secretKey, parameterSpec);
        counter++; // update counter for next message
        return cipher.doFinal(message, 0, len);
    }
}
