package www.webserver.com;

import java.io.*;

public class WebUiBuilder {

    public static void writeHtmlPrologue(PrintWriter out, String ip, int port) {
        WebUiHtml.writeHtmlPrologue(out, ip, port);
    }

    public static void writeDirectoryHeader(PrintWriter out, boolean showActions) {
        WebUiHtml.writeDirectoryHeader(out, showActions);
    }

    public static void writeParentLink(PrintWriter out, String parentAbs, int colspan) {
        WebUiHtml.writeParentLink(out, parentAbs, colspan);
    }

    public static void writeFileRow(PrintWriter out, StorageItem item, String absoluteBase, boolean showActions) {
        WebUiHtml.writeFileRow(out, item, absoluteBase, showActions);
    }

    public static void writeTruncatedNote(PrintWriter out, int colspan) {
        WebUiHtml.writeTruncatedNote(out, colspan);
    }

    public static void writeUploadForm(PrintWriter out) {
        WebUiHtml.writeUploadForm(out);
    }

    public static void writeFooter(PrintWriter out, String ip, int port) {
        WebUiHtml.writeFooter(out, ip, port);
    }

    public static void writeJavaScript(PrintWriter out, String absoluteBase) {
        writeJavaScript(out, absoluteBase, GlobalVars.hostHtmlEnabled);
    }

    public static void writeJavaScript(PrintWriter out, String absoluteBase, boolean hostHtmlEnabled) {
        WebUiHtml.writeJavaScript(out, absoluteBase, hostHtmlEnabled);
    }

    public static void write404ErrorPage(OutputStream rawOut) {
        WebUiHtml.write404ErrorPage(rawOut);
    }
}