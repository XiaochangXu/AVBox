-keepattributes *Annotation*,InnerClasses
-keepattributes EnclosingMethod, InnerClasses
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes LineNumberTable
-renamesourcefileattribute SourceFile

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View

-dontwarn androidx.**

-keep class org.xmlpull.v1.** {*;}

-dontwarn org.fourthline.cling.**
-keep class org.fourthline.cling.** { *; }

-keep class **.R$* {*;}

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class * extends android.app.Activity{
    public void *(android.view.View);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep public class * extends android.view.View{
    *** get*();
    void set*(***);
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keep public class * extends androidx.recyclerview.widget.RecyclerView$LayoutManager{
    *** get*();
    void set*(***);
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keepclassmembers class * {
    void *(**On*Event);
    void *(**On*Listener);
}
-dontwarn android.view.**
-dontwarn android.media.**
-dontwarn okhttp3.**
-keep class okhttp3.**{*;}
-dontwarn okio.**
-keep class okio.**{*;}
-dontwarn sun.misc.**
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowoptimization class * extends com.google.gson.reflect.TypeToken
-keep class com.thoughtworks.xstream.converters.extended.SubjectConverter { *; }
-keep class com.thoughtworks.xstream.converters.extended.ThrowableConverter { *; }
-keep class com.thoughtworks.xstream.converters.extended.StackTraceElementConverter { *; }
-keep class com.thoughtworks.xstream.converters.extended.CurrencyConverter { *; }
-keep class com.thoughtworks.xstream.converters.extended.RegexPatternConverter { *; }
-keep class com.thoughtworks.xstream.converters.extended.CharsetConverter { *; }
-keep class com.thoughtworks.xstream.** { *; }
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}

-keep class com.xunlei.downloadlib.** {*;}
-keep class com.whl.quickjs.** {*;}

-dontwarn androidx.media3.**

-keep class com.github.tvbox.osc.bean.** { *; }

-keep class com.google.gson.**{*;}
-keep class com.google.zxing.**{*;}
-keep class com.google.common.** { *; }
-keep class com.thegrizzlylabs.sardineandroid.** { *; }
-keeppackagenames kotlin.**
-keepclassmembers enum com.materialkolor.PaletteStyle {
    <fields>;
}
-keep class fi.iki.elonen.** { *; }

-keep public class com.undcover.freedom.pyramid.** { *; }
-dontwarn com.undcover.freedom.pyramid.**
-keep public class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

-keep class com.github.tvbox.osc.** { *; }
-keep class com.github.catvod.** { *; }

-dontwarn com.bea.xml.stream.MXParserFactory
-dontwarn com.bea.xml.stream.XMLOutputFactoryBase
-dontwarn com.ctc.wstx.stax.WstxInputFactory
-dontwarn com.ctc.wstx.stax.WstxOutputFactory
-dontwarn java.awt.BorderLayout
-dontwarn java.awt.Color
-dontwarn java.awt.Component
-dontwarn java.awt.Container
-dontwarn java.awt.Dimension
-dontwarn java.awt.Font
-dontwarn java.awt.LayoutManager
-dontwarn java.awt.Rectangle
-dontwarn java.awt.Toolkit
-dontwarn java.awt.Window
-dontwarn java.awt.datatransfer.Clipboard
-dontwarn java.awt.datatransfer.ClipboardOwner
-dontwarn java.awt.datatransfer.StringSelection
-dontwarn java.awt.datatransfer.Transferable
-dontwarn java.awt.event.ActionEvent
-dontwarn java.awt.event.ActionListener
-dontwarn java.awt.event.ItemListener
-dontwarn java.awt.event.WindowEvent
-dontwarn java.awt.event.WindowListener
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn java.beans.PropertyEditor
-dontwarn javax.activation.ActivationDataFlavor
-dontwarn javax.swing.AbstractAction
-dontwarn javax.swing.AbstractButton
-dontwarn javax.swing.BorderFactory
-dontwarn javax.swing.Box
-dontwarn javax.swing.BoxLayout
-dontwarn javax.swing.Icon
-dontwarn javax.swing.ImageIcon
-dontwarn javax.swing.JButton
-dontwarn javax.swing.JCheckBox
-dontwarn javax.swing.JComboBox
-dontwarn javax.swing.JDialog
-dontwarn javax.swing.JFrame
-dontwarn javax.swing.JLabel
-dontwarn javax.swing.JPanel
-dontwarn javax.swing.JScrollPane
-dontwarn javax.swing.JTable
-dontwarn javax.swing.JToolBar
-dontwarn javax.swing.ListSelectionModel
-dontwarn javax.swing.SwingUtilities
-dontwarn javax.swing.border.Border
-dontwarn javax.swing.border.TitledBorder
-dontwarn javax.swing.event.ListSelectionListener
-dontwarn javax.swing.plaf.FontUIResource
-dontwarn javax.swing.table.AbstractTableModel
-dontwarn javax.swing.table.DefaultTableCellRenderer
-dontwarn javax.swing.table.JTableHeader
-dontwarn javax.swing.table.TableCellRenderer
-dontwarn javax.swing.table.TableColumn
-dontwarn javax.swing.table.TableColumnModel
-dontwarn javax.swing.table.TableModel
-dontwarn javax.xml.bind.DatatypeConverter
-dontwarn javax.xml.stream.Location
-dontwarn javax.xml.stream.XMLInputFactory
-dontwarn javax.xml.stream.XMLOutputFactory
-dontwarn javax.xml.stream.XMLStreamException
-dontwarn javax.xml.stream.XMLStreamReader
-dontwarn javax.xml.stream.XMLStreamWriter
-dontwarn net.sf.cglib.proxy.Callback
-dontwarn net.sf.cglib.proxy.CallbackFilter
-dontwarn net.sf.cglib.proxy.Enhancer
-dontwarn net.sf.cglib.proxy.Factory
-dontwarn net.sf.cglib.proxy.NoOp
-dontwarn net.sf.cglib.proxy.Proxy
-dontwarn nu.xom.Attribute
-dontwarn nu.xom.Builder
-dontwarn nu.xom.Document
-dontwarn nu.xom.Element
-dontwarn nu.xom.Elements
-dontwarn nu.xom.Node
-dontwarn nu.xom.ParentNode
-dontwarn nu.xom.ParsingException
-dontwarn nu.xom.Text
-dontwarn nu.xom.ValidityException
-dontwarn org.codehaus.jettison.AbstractXMLStreamWriter
-dontwarn org.codehaus.jettison.mapped.Configuration
-dontwarn org.codehaus.jettison.mapped.MappedNamespaceConvention
-dontwarn org.codehaus.jettison.mapped.MappedXMLInputFactory
-dontwarn org.codehaus.jettison.mapped.MappedXMLOutputFactory
-dontwarn org.dom4j.Attribute
-dontwarn org.dom4j.Branch
-dontwarn org.dom4j.Document
-dontwarn org.dom4j.DocumentException
-dontwarn org.dom4j.DocumentFactory
-dontwarn org.dom4j.Element
-dontwarn org.dom4j.io.OutputFormat
-dontwarn org.dom4j.io.SAXReader
-dontwarn org.dom4j.io.XMLWriter
-dontwarn org.dom4j.tree.DefaultElement
-dontwarn org.jdom.Attribute
-dontwarn org.jdom.Content
-dontwarn org.jdom.DefaultJDOMFactory
-dontwarn org.jdom.Document
-dontwarn org.jdom.Element
-dontwarn org.jdom.JDOMException
-dontwarn org.jdom.JDOMFactory
-dontwarn org.jdom.Text
-dontwarn org.jdom.input.SAXBuilder
-dontwarn org.jdom2.Attribute
-dontwarn org.jdom2.Content
-dontwarn org.jdom2.DefaultJDOMFactory
-dontwarn org.jdom2.Document
-dontwarn org.jdom2.Element
-dontwarn org.jdom2.JDOMException
-dontwarn org.jdom2.JDOMFactory
-dontwarn org.jdom2.Text
-dontwarn org.jdom2.input.SAXBuilder
-dontwarn org.joda.time.DateTime
-dontwarn org.joda.time.DateTimeZone
-dontwarn org.joda.time.format.DateTimeFormatter
-dontwarn org.joda.time.format.ISODateTimeFormat
-dontwarn org.kxml2.io.KXmlParser
-dontwarn org.xmlpull.mxp1.MXParser

-dontwarn com.kyant.backdrop.**
-dontwarn com.kyant.capsule.**
-dontwarn com.kyant.shapes.**

-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
    public static *** println(...);
}

-assumenosideeffects class com.github.catvod.crawler.SpiderDebug {
    public static *** log(...);
}

-assumenosideeffects class com.github.tvbox.osc.util.LOG {
    public static *** d(...);
    public static *** i(...);
    public static *** e(...);
    public static *** longI(...);
    public static *** longE(...);
}
