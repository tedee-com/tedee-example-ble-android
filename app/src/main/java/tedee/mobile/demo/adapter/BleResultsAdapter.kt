package tedee.mobile.demo.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import tedee.mobile.demo.R
import tedee.mobile.demo.databinding.BleResultItemBinding

class BleResultsAdapter : RecyclerView.Adapter<BleResultsAdapter.ViewHolder>() {

  private val asyncDiffer = AsyncListDiffer(this, getDiffCallback<BleResultItem>())

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.ble_result_item, parent, false)
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    asyncDiffer.currentList[position].let { holder.bind(it) }
  }

  override fun getItemCount(): Int = asyncDiffer.currentList.size

  fun addItems(bleResultItems: List<BleResultItem>) {
    asyncDiffer.submitList(bleResultItems)
  }

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val binding = BleResultItemBinding.bind(itemView)

    fun bind(item: BleResultItem) {
      binding.commandResult.text = item.message
    }
  }

  private fun <T : Any> getDiffCallback(compare: ((oldItem: T, newItem: T) -> Boolean)? = null) =
    object : DiffUtil.ItemCallback<T>() {
      override fun areItemsTheSame(oldItem: T, newItem: T) =
        compare?.invoke(oldItem, newItem) ?: false

      @SuppressLint("DiffUtilEquals")
      override fun areContentsTheSame(oldItem: T, newItem: T) = oldItem == newItem
    }
}
