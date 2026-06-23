package www.webserver.com;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class WebUiHtml {

    public static void writeHtmlPrologue(PrintWriter out, String ip, int port) {
        out.print("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n<title>Web Server</title>\n");
        out.print("<style>" + WebUiCss.CSS + "</style>\n</head>\n<body>\n");
    }

    public static void writeDirectoryHeader(PrintWriter out, boolean showActions) {
        out.print("<div class=\"container\">\n<table>\n");
        if (showActions) {
            out.print("<tr><th style=\"width:50%\">Name</th><th style=\"width:20%;text-align:center\">Size</th><th style=\"width:20%;text-align:center\">Actions</th><th style=\"width:10%;text-align:right\">Download</th></tr>\n");
        } else {
            out.print("<tr><th style=\"width:60%\">Name</th><th style=\"width:30%;text-align:center\">Size</th><th style=\"width:10%;text-align:right\">Download</th></tr>\n");
        }
    }

    public static void writeParentLink(PrintWriter out, String parentAbs, int colspan) {
        out.print("<tr><td class=\"name\" colspan=\"" + colspan + "\"><a href=\"" +
                  ServerHandler.escapeHtmlStatic(parentAbs) +
                  "\" class=\"dir-link\"><span class=\"parent-icon\"></span>Parent Directory</a></td></tr>\n");
    }

    public static void writeFileRow(PrintWriter out, StorageItem item, String absoluteBase, boolean showActions) {
        String encodedName = UrlUtils.encodePathSegment(item.name);
        String itemAbs = absoluteBase + "/" + encodedName;
        itemAbs = itemAbs.replaceAll("//+", "/");

        out.print("<tr>");
        out.print("<td class=\"name\"><a href=\"" + ServerHandler.escapeHtmlStatic(itemAbs) + "\" class=\"" + (item.isDirectory ? "dir-link" : "file-link") + "\">");
        out.print(item.isDirectory ? "<span class=\"folder-icon\"></span>" : "<span class=\"file-icon\"></span>");
        out.print(ServerHandler.escapeHtmlStatic(item.name) + "</a></td>");

        if (item.isDirectory) {
            out.print("<td class=\"size\" style=\"text-align:center;\">-</td>");
        } else {
            String sizeText = ServerHandler.formatSizeStatic(item.size);
            int spaceIdx = sizeText.indexOf(' ');
            String num = sizeText.substring(0, spaceIdx);
            String unit = sizeText.substring(spaceIdx + 1);
            out.print("<td class=\"size\"><span class=\"size-num\">" + num + "</span> <span class=\"size-unit\">" + unit + "</span></td>");
        }

        if (showActions) {
            out.print("<td style=\"text-align:center; vertical-align:middle;\">");
            out.print("<button onclick=\"deleteItem('" + ServerHandler.escapeJsStatic(item.name) + "')\" title=\"Delete\">🗑️</button> ");
            out.print("<button onclick=\"renameItem('" + ServerHandler.escapeJsStatic(item.name) + "')\" title=\"Rename\">📝</button>");
            out.print("</td>");
        }
        out.print("<td class=\"download\"><button onclick=\"downloadItem('" + ServerHandler.escapeJsStatic(item.name) + "', " + item.isDirectory + ")\" title=\"Download\">📥</button></td>");
        out.print("</tr>\n");
    }

    public static void writeTruncatedNote(PrintWriter out, int colspan) {
        out.print("<tr class=\"more-row\"><td colspan=\"" + colspan + "\"><a href=\"javascript:void(0)\" onclick=\"loadMoreItems(500)\">More...</a></td></tr>");
    }

    public static void writeUploadForm(PrintWriter out) {
        out.print("</table>\n<div class=\"upload-section\">\n<form class=\"upload-form\" id=\"uploadForm\" method=\"POST\" enctype=\"multipart/form-data\">\n<div class=\"upload-row\">\n<div class=\"chunk-toggle\"><span>Chunks Engine</span><label class=\"switch\"><input type=\"checkbox\" id=\"chunksMode\"><span class=\"slider\"></span></label></div>\n<button type=\"button\" class=\"btn-choose\" id=\"chooseFileBtn\">Choose File</button>\n</div>\n<div class=\"file-info-card\" id=\"fileInfoCard\" style=\"display:none;\">\n<div class=\"file-icon-display\" id=\"fileIconDisplay\">📄</div>\n<div class=\"file-details\">\n<div class=\"file-name-display\" id=\"fileNameDisplay\">—</div>\n<div class=\"file-meta\"><span id=\"fileSizeDisplay\">—</span><span id=\"fileExtDisplay\">—</span></div>\n</div>\n</div>\n<button type=\"button\" id=\"clearFileBtn\" style=\"display:none;margin-top:8px;background:#f44336;color:white;border:none;padding:6px 12px;border-radius:4px;cursor:pointer;\">Clear selection</button>\n<input type=\"file\" id=\"myFile\" name=\"filename\" multiple>\n<div class=\"upload-btn-container\"><button type=\"submit\" class=\"upload-btn\" id=\"uploadBtn\">Upload</button></div>\n</form>\n</div>\n");
        out.print("<div class=\"folder-btn-container\"><button class=\"btn-new-folder\" onclick=\"createFolder()\">Create New Folder</button></div>\n");
        out.print("<div class=\"folder-btn-container\" style=\"margin-top:8px;\"><button class=\"btn-new-folder\" onclick=\"createFile()\">Create New File</button></div>\n");
    }

    public static void writeFooter(PrintWriter out, String ip, int port) {
        out.print("<div class=\"footer\">Web Server running on " + ip + ":" + port + "</div>\n</div>\n");
    }

    public static void writeJavaScript(PrintWriter out, String absoluteBase, boolean hostHtmlEnabled) {
        out.print("<script>\n");
        out.print("var hostHtmlEnabled = " + hostHtmlEnabled + ";\n");
        out.print("function escapeHtml(text){var d=document.createElement('div');d.appendChild(document.createTextNode(text));return d.innerHTML;}\n");
        out.print("var serverBase = '" + ServerHandler.escapeJsStatic(absoluteBase) + "';\n");
        out.print("if(!serverBase.endsWith('/')) serverBase += '/';\n");
        out.print("var currentFolder = serverBase;\n");
        out.print(WebUiJs.JS_FUNCTIONS);
        out.print("</script>\n");
        out.print("</body>\n</html>");
    }

    public static void write404ErrorPage(OutputStream rawOut) {
        try {
            String html = "<!DOCTYPE html>\n<html><head>\n<meta charset=\"UTF-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n<title>404 Not Found</title>\n<style>" +
                          WebUiCss.ERROR_404_CSS +
                          "</style>\n</head><body>\n<div class=\"error-card\">\n  <h1>404</h1>\n  <div class=\"not-found-text\">Not Found</div>\n  <a href=\"/\" class=\"home-link\">REDIRECT</a>\n</div>\n</body></html>";
            byte[] content = html.getBytes(StandardCharsets.UTF_8);
            String hdr = "HTTP/1.1 404 NOT FOUND\r\n" +
                         "Server: WebServer/4.0\r\n" +
                         "Content-Length: " + content.length + "\r\n" +
                         "Connection: close\r\n" +
                         "Content-Type: text/html; charset=UTF-8\r\n\r\n";
            rawOut.write(hdr.getBytes(StandardCharsets.ISO_8859_1));
            rawOut.write(content);
            rawOut.flush();
        } catch (Exception ignored) {}
    }
}