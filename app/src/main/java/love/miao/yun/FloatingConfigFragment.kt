package love.miao.yun

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import love.miao.yun.ui.screens.FloatingConfigScreen
import love.miao.yun.ui.theme.NekoNekoTheme

class FloatingConfigFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            NekoNekoTheme {
                FloatingConfigScreen()
            }
        }
    }
}
