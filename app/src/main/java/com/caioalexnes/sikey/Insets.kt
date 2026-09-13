package com.caioalexnes.sikey

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Afasta o conteúdo das barras do sistema.
 *
 * Do Android 15 em diante a janela é desenhada de ponta a ponta por padrão, e
 * sem isto a barra de navegação fica POR CIMA da última linha da lista — que
 * neste app é justamente um hash, o dado que a pessoa veio ler.
 *
 * O teclado entra na conta junto: a caixa de comparação fica no meio da tela
 * de detalhes e precisa continuar visível enquanto se digita.
 */
fun View.applySystemInsets(baseHorizontalDp: Int = 0, baseBottomDp: Int = 0) {
    val density = resources.displayMetrics.density
    val horizontal = (baseHorizontalDp * density).toInt()
    val bottom = (baseBottomDp * density).toInt()

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or
                WindowInsetsCompat.Type.ime(),
        )
        view.updatePadding(
            left = horizontal + bars.left,
            right = horizontal + bars.right,
            bottom = bottom + bars.bottom,
        )
        insets
    }
}
