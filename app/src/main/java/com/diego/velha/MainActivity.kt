package com.diego.velha

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min
import kotlin.random.Random

class MainActivity : Activity() {

    enum class Mode(val label: String) {
        DOIS("2 jogadores"),
        FACIL("vs CPU fácil"),
        DIFICIL("vs CPU imbatível")
    }

    companion object {
        private val BG = Color.parseColor("#121417")
        private val CARD = Color.parseColor("#1E2228")
        private val GRID = Color.parseColor("#5A606B")
        private val X_COLOR = Color.parseColor("#4FC3F7")
        private val O_COLOR = Color.parseColor("#FF8A65")
        private val TEXT = Color.parseColor("#ECEFF1")
        private val MUTED = Color.parseColor("#9AA3AD")

        private val LINES = arrayOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6)
        )
    }

    // Estado do jogo: 0 = vazio, 1 = X, 2 = O (a CPU joga sempre de O)
    private val board = IntArray(9)
    private var turn = 1
    private var starter = 1
    private var winner = 0 // 0 em jogo, 1 X, 2 O, 3 empate
    private var winLine: IntArray? = null
    private var mode = Mode.DIFICIL
    private var scoreX = 0
    private var scoreO = 0
    private var scoreDraw = 0
    private var cpuThinking = false

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var score: TextView
    private lateinit var modeButton: Button
    private lateinit var boardView: BoardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        savedInstanceState?.let { restore(it) }
        buildUi()
        maybeCpu()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        buildUi() // tela do carro girou: refaz o layout mantendo a partida
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putIntArray("board", board)
        out.putInt("turn", turn)
        out.putInt("starter", starter)
        out.putInt("winner", winner)
        out.putIntArray("winLine", winLine)
        out.putInt("mode", mode.ordinal)
        out.putIntArray("scores", intArrayOf(scoreX, scoreO, scoreDraw))
    }

    private fun restore(b: Bundle) {
        b.getIntArray("board")?.copyInto(board)
        turn = b.getInt("turn", 1)
        starter = b.getInt("starter", 1)
        winner = b.getInt("winner", 0)
        winLine = b.getIntArray("winLine")
        mode = Mode.values()[b.getInt("mode", Mode.DIFICIL.ordinal)]
        b.getIntArray("scores")?.let { scoreX = it[0]; scoreO = it[1]; scoreDraw = it[2] }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------- Interface ----------

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun label(sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun bigButton(text: String, color: Int, onTap: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        minHeight = dp(64)
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(color)
        }
        setOnClickListener { onTap() }
    }

    private fun buildUi() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val root = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BG)
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val title = label(30f, TEXT, bold = true).apply { text = "Jogo da Velha" }
        status = label(24f, TEXT, bold = true)
        score = label(18f, MUTED)
        modeButton = bigButton("", CARD) { nextMode() }
        val newGameButton = bigButton("Nova partida", Color.parseColor("#2E7D5B")) { newGame() }
        val resetButton = bigButton("Zerar placar", CARD) { resetScores() }

        boardView = BoardView()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        fun gap(h: Int) = View(this).also { panel.addView(it, LinearLayout.LayoutParams(1, dp(h))) }

        panel.addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        gap(16)
        panel.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        gap(8)
        panel.addView(score, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        gap(24)
        panel.addView(modeButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        gap(12)
        panel.addView(newGameButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        gap(12)
        panel.addView(resetButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        if (landscape) {
            root.addView(boardView, LinearLayout.LayoutParams(0, MATCH_PARENT, 1.3f))
            root.addView(View(this), LinearLayout.LayoutParams(dp(32), 1))
            root.addView(panel, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        } else {
            root.addView(boardView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            root.addView(View(this), LinearLayout.LayoutParams(1, dp(16)))
            root.addView(panel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        setContentView(root)
        refresh()
    }

    private fun refresh() {
        val vsCpu = mode != Mode.DOIS
        val nameX = if (vsCpu) "Você (X)" else "X"
        val nameO = if (vsCpu) "CPU (O)" else "O"

        status.text = when (winner) {
            1 -> if (vsCpu) "Você venceu! 🎉" else "X venceu! 🎉"
            2 -> if (vsCpu) "A CPU venceu" else "O venceu! 🎉"
            3 -> "Deu velha! (empate)"
            else -> when {
                cpuThinking -> "CPU pensando…"
                vsCpu && turn == 1 -> "Sua vez"
                else -> "Vez de ${if (turn == 1) "X" else "O"}"
            }
        }
        status.setTextColor(
            when {
                winner == 1 || (winner == 0 && turn == 1) -> X_COLOR
                winner == 2 || (winner == 0 && turn == 2) -> O_COLOR
                else -> TEXT
            }
        )
        score.text = "$nameX  $scoreX   ·   Empates  $scoreDraw   ·   $nameO  $scoreO"
        modeButton.text = "Modo: ${mode.label}"
        boardView.invalidate()
    }

    // ---------- Regras ----------

    private fun newGame() {
        handler.removeCallbacksAndMessages(null)
        cpuThinking = false
        board.fill(0)
        winner = 0
        winLine = null
        starter = 3 - starter // alterna quem começa
        turn = starter
        refresh()
        maybeCpu()
    }

    private fun resetScores() {
        scoreX = 0; scoreO = 0; scoreDraw = 0
        newGame()
    }

    private fun nextMode() {
        mode = Mode.values()[(mode.ordinal + 1) % Mode.values().size]
        starter = 2 // garante que a primeira partida do novo modo comece com X
        resetScores()
    }

    private fun onCellTapped(i: Int) {
        if (winner != 0) { newGame(); return } // toque no tabuleiro após o fim = nova partida
        if (cpuThinking || board[i] != 0) return
        if (mode != Mode.DOIS && turn == 2) return
        play(i)
        maybeCpu()
    }

    private fun play(i: Int) {
        board[i] = turn
        val (w, line) = checkWinner(board)
        if (w != 0) {
            winner = w
            winLine = line
            when (w) { 1 -> scoreX++; 2 -> scoreO++; else -> scoreDraw++ }
        } else {
            turn = 3 - turn
        }
        refresh()
    }

    private fun maybeCpu() {
        if (mode == Mode.DOIS || winner != 0 || turn != 2) return
        cpuThinking = true
        refresh()
        handler.postDelayed({
            cpuThinking = false
            if (winner == 0 && turn == 2) play(cpuMove()) else refresh()
        }, 500)
    }

    private fun checkWinner(b: IntArray): Pair<Int, IntArray?> {
        for (l in LINES) {
            if (b[l[0]] != 0 && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]] to l
        }
        return if (b.all { it != 0 }) 3 to null else 0 to null
    }

    private fun cpuMove(): Int {
        val empty = (0 until 9).filter { board[it] == 0 }
        // Fácil: joga bem só às vezes
        if (mode == Mode.FACIL && Random.nextFloat() > 0.35f) return empty.random()
        return bestMove()
    }

    private fun bestMove(): Int {
        val b = board.copyOf()
        var best = -1
        var bestScore = Int.MIN_VALUE
        for (i in (0 until 9).shuffled()) {
            if (b[i] != 0) continue
            b[i] = 2
            val s = minimax(b, 1, 1)
            b[i] = 0
            if (s > bestScore) { bestScore = s; best = i }
        }
        return best
    }

    private fun minimax(b: IntArray, player: Int, depth: Int): Int {
        when (checkWinner(b).first) {
            2 -> return 10 - depth
            1 -> return depth - 10
            3 -> return 0
        }
        var best = if (player == 2) Int.MIN_VALUE else Int.MAX_VALUE
        for (i in 0 until 9) {
            if (b[i] != 0) continue
            b[i] = player
            val s = minimax(b, 3 - player, depth + 1)
            b[i] = 0
            best = if (player == 2) maxOf(best, s) else minOf(best, s)
        }
        return best
    }

    // ---------- Tabuleiro desenhado ----------

    inner class BoardView : View(this@MainActivity) {
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GRID; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = X_COLOR; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val oPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = O_COLOR; style = Paint.Style.STROKE
        }
        private val winPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; alpha = 230
        }

        private fun area(): RectF {
            val s = min(width, height) * 0.95f
            val l = (width - s) / 2f
            val t = (height - s) / 2f
            return RectF(l, t, l + s, t + s)
        }

        private fun center(r: RectF, i: Int): Pair<Float, Float> {
            val cell = r.width() / 3f
            return (r.left + cell * (i % 3) + cell / 2f) to (r.top + cell * (i / 3) + cell / 2f)
        }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val r = area()
            val cell = r.width() / 3f
            gridPaint.strokeWidth = cell * 0.05f
            xPaint.strokeWidth = cell * 0.1f
            oPaint.strokeWidth = cell * 0.1f
            winPaint.strokeWidth = cell * 0.08f

            val inset = cell * 0.06f
            for (k in 1..2) {
                val x = r.left + cell * k
                val y = r.top + cell * k
                c.drawLine(x, r.top + inset, x, r.bottom - inset, gridPaint)
                c.drawLine(r.left + inset, y, r.right - inset, y, gridPaint)
            }

            val h = cell * 0.27f
            for (i in 0 until 9) {
                val (cx, cy) = center(r, i)
                when (board[i]) {
                    1 -> {
                        c.drawLine(cx - h, cy - h, cx + h, cy + h, xPaint)
                        c.drawLine(cx + h, cy - h, cx - h, cy + h, xPaint)
                    }
                    2 -> c.drawCircle(cx, cy, h * 1.05f, oPaint)
                }
            }

            winLine?.let { line ->
                val (ax, ay) = center(r, line[0])
                val (bx, by) = center(r, line[2])
                val ex = (bx - ax) * 0.15f
                val ey = (by - ay) * 0.15f
                c.drawLine(ax - ex, ay - ey, bx + ex, by + ey, winPaint)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) {
                val r = area()
                if (r.contains(e.x, e.y)) {
                    val cell = r.width() / 3f
                    val col = ((e.x - r.left) / cell).toInt().coerceIn(0, 2)
                    val row = ((e.y - r.top) / cell).toInt().coerceIn(0, 2)
                    onCellTapped(row * 3 + col)
                }
                performClick()
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
