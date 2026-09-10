@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.view

class Menu {
    fun add(itemId: Int, order: Int, titleRes: Int): MenuItem = MenuItem()
    fun add(groupId: Int, itemId: Int, order: Int, titleRes: Int): MenuItem = MenuItem()
    fun add(groupId: Int, itemId: Int, order: Int, title: CharSequence): MenuItem = MenuItem()
    fun addSubMenu(titleRes: Int): Menu = Menu()
    fun findItem(itemId: Int): MenuItem? = null
    fun setGroupDividerEnabled(enabled: Boolean) {}
    fun clear() {}
}

class MenuItem {
    val itemId: Int = 0
    fun setVisible(visible: Boolean) {}
    fun setEnabled(enabled: Boolean) {}
    fun setIcon(iconRes: Int): MenuItem = this
    fun setIcon(icon: android.graphics.drawable.Drawable?): MenuItem = this
    fun setCheckable(checkable: Boolean): MenuItem = this
    fun setChecked(checked: Boolean): MenuItem = this
    val isChecked: Boolean get() = false
    fun setTitle(title: CharSequence) {}
    fun setOnMenuItemClickListener(listener: OnMenuItemClickListener?): MenuItem = this

    fun interface OnMenuItemClickListener {
        fun onMenuItemClick(item: MenuItem): Boolean
    }
}
