@file:Suppress("DEPRECATION")

import org.jsoup.Jsoup
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.*
import java.util.*
import javax.swing.JEditorPane
import javax.swing.JFrame
import javax.swing.UIManager
import javax.swing.text.StyleConstants
import javax.swing.text.html.HTMLDocument

//ver 0.1 20240606 first release
//ver 0.2 20240622 for result with many bacteria
//ver 0.3 培養結果ウィンドウでCTRL+A CTRL+Cしてから変換ボタンを押す仕様に変更, その他、連絡情報

fun main()
{
    val f = JFrame("Culture Result Rewriter 0.3")
    val g = gui()
    UIManager.put("OptionPane.messageFont", Font("Dialog", Font.PLAIN, 12))
    UIManager.put("OptionPane.buttonFont", Font("Dialog", Font.PLAIN, 12))
    initFont(g.text2)
    f.contentPane = g.panel
    f.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    f.setSize(800, 600)
    f.isResizable = false
    f.setLocationRelativeTo(null)
    f.isVisible = true

    val tx = getClipboardString()
    g.text1.text = tx

    g.convertButton.addActionListener {
        convertButton(g)
        val doc = Jsoup.parse(g.text2.text)
        val tds = doc.body().html()
        setClipboardHTMLString(tds)
    }

}

/**
 * newTxt内にエラーメッセージを設定する.
 */
fun notCultureData(g: gui)
{
    g.text2.text = "<html>培養結果ウィンドウでCTRL+A CTRL+Cしてから変換ボタンを押してください</html>"
}

fun convertButton(g: gui)
{
    val tx = g.text1.text
    var tt = ""
    val st = StringTokenizer(tx, "\n")
    if (!st.hasMoreTokens())
    {
        notCultureData(g)
        return
    }

    var t: String
    while(st.hasMoreTokens())
    {
        t = st.nextToken().trim { it <= ' ' }
        if(t.startsWith("培養同定"))    break
    }
    if (!st.hasMoreTokens())
    {
        notCultureData(g)
        return
    }

    // 培養同定菌
    var bacnum = 0 // 菌の数
    while (st.hasMoreTokens())
    {
        t = st.nextToken().trim { it <= ' ' }
        if(t=="") continue
        if(t.startsWith("感受性")) break
        tt += "$t<br>"
        bacnum += 1 // 「感受性」のヘッダが出るまでに出現した菌の数を１つ増やす
    }

    if (!st.hasMoreTokens())
    {
        notCultureData(g)
        return
    }

    // 感受性結果が２種類の場合はどうするか.. 区切りもスペースで見分けがつかない
    // 1.HTMLで保存できないものか..ちょっと無理そうだな
    // 2.培養同定菌名のときの行数で複数かどうかをみわける　今回はこれを使う
    // 3.培養菌名で区切るやり方　すべての菌名の辞書もつ？　現実的じゃあないな

    tt += "感受性結果<br>"
    while (st.hasMoreTokens())
    {
        t = st.nextToken().trim { it <= ' ' }
        if(t=="") continue
        if(t.startsWith("----")) break

        tt += t  // SBT/ABPC ≦2
        if (!st.hasMoreTokens()) break
        for ( num in 1..bacnum)
        {
            t = st.nextToken().trim { it <= ' ' }
            if (!st.hasMoreTokens()) break
            tt += " $t"
            t = st.nextToken().trim { it <= ' ' }
            tt += " $t"
            if (!st.hasMoreTokens()) break
        }
        t = st.nextToken().trim { it <= ' ' } // skip 1 line
        tt += "$t<br>"
    }

    // 追加情報の追加
    while (st.hasMoreTokens())
    {
        t = st.nextToken().trim { it <= ' ' }
        if(t=="") continue
        if(t.startsWith("連絡情報"))
        {
            t = t.substring(4) // 連絡情報の文字列削除
            tt += "$t<br>"
            continue
        }
        if(t.startsWith("肺炎球菌"))
        {
            tt += "$t<br>"
            continue
        }

    }

    g.text2.text = "<font size=2 face=\"&#65325;&#65331; &#12468;&#12471;&#12483;&#12463;\">$tt</font>"
}

private fun initFont(editor: JEditorPane)
{
    val doc = editor.document as HTMLDocument
    val ss = doc.styleSheet
    val sss = ss.styleSheets
    for (i in sss.indices.reversed())
    {
        val body = sss[i].getStyle("body") // StyleはAttributeSetの具象クラス
        if (body != null)
        {
            StyleConstants.setFontFamily(body, "Dialog")
            StyleConstants.setFontSize(body, 12)
            break
        }
    }
}


private class HtmlSelection(private val html: String) : Transferable
{
    companion object
    {
        private val htmlFlavors: ArrayList<DataFlavor> = ArrayList<DataFlavor>()

        init
        {
            try
            {
                htmlFlavors.add(
                    DataFlavor(
                        "text/html;class=java.lang.String"
                    )
                )
                htmlFlavors
                    .add(DataFlavor("text/html;class=java.io.Reader"))
                htmlFlavors.add(
                    DataFlavor(
                        "text/html;charset=unicode;class=java.io.InputStream"
                    )
                )
            } catch (ex: ClassNotFoundException)
            {
                ex.printStackTrace()
            }
        }
    }

    override fun getTransferDataFlavors(): Array<DataFlavor>
    {
        return htmlFlavors.toTypedArray()
    }

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean
    {
        return htmlFlavors.contains(flavor)
    }

    @Throws(UnsupportedFlavorException::class)
    override fun getTransferData(flavor: DataFlavor): Any
    {
        return when
        {
            String::class.java == flavor.representationClass -> html
            Reader::class.java == flavor.representationClass -> StringReader(html)
            InputStream::class.java == flavor.representationClass -> StringBufferInputStream(html)
            else -> throw UnsupportedFlavorException(flavor)
        }
    }
}

fun setClipboardHTMLString(text: String)
{
    val kit = Toolkit.getDefaultToolkit()
    val clip = kit.systemClipboard
    val t: Transferable = HtmlSelection(text)
    clip.setContents(t, null)
}
/*
fun setClipboardString(text: String)
{
    val kit = Toolkit.getDefaultToolkit()
    val clip = kit.systemClipboard
    val ss = StringSelection(text)
    clip.setContents(ss, ss)
}
*/

fun getClipboardString(): String
{
    val kit = Toolkit.getDefaultToolkit()
    val clip = kit.systemClipboard
    val contents = clip.getContents(null)
    var result = ""
    val hasTransferableText = (contents != null
            && contents.isDataFlavorSupported(DataFlavor.stringFlavor))
    if (hasTransferableText)
    {
        try
        {
            result = contents
                .getTransferData(DataFlavor.stringFlavor) as String
        } catch (ex: UnsupportedFlavorException)
        {
            // highly unlikely since we are using a standard DataFlavor
            println(ex)
            ex.printStackTrace()
        } catch (ex: IOException)
        {
            println(ex)
            ex.printStackTrace()
        }
    }
    return result
}
