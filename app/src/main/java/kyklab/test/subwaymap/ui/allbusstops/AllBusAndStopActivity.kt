package kyklab.test.subwaymap.ui.allbusstops

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.activity_all_bus_stop_view.*
import kyklab.test.subwaymap.R

class AllBusAndStopActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_bus_stop_view)

        vp.adapter = ScreenSlidePagerAdapter(this)
        TabLayoutMediator(tab, vp) { tab, position ->
            tab.text = when (position) {
                0 -> "Bus"
                1 -> "Stop"
                else -> "wtf?"
            }
        }.attach()
    }

    private inner class ScreenSlidePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment =
            when (position) {
                0 -> AllBusesFragment()
                else -> AllStopsFragment()
            }
    }
}