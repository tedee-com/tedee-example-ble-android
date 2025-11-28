import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'screens/simple_lock_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  // Enable fullscreen mode
  SystemChrome.setEnabledSystemUIMode(
    SystemUiMode.immersiveSticky,
    overlays: [],
  );

  runApp(const TedeeFlutterApp());
}

class TedeeFlutterApp extends StatelessWidget {
  const TedeeFlutterApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Tedee Lock',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        useMaterial3: true,
      ),
      home: const SimpleLockScreen(),
      debugShowCheckedModeBanner: false,
    );
  }
}
