/*
Copyright (C) 2012 Haowen Ning

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/
package org.liberty.android.fantastischmemo.downloader.google;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.apache.mycommons.io.IOUtils;

import org.liberty.android.fantastischmemo.downloader.DownloaderUtils;

import org.liberty.android.fantastischmemo.downloader.google.Spreadsheet;
import org.liberty.android.fantastischmemo.downloader.google.Spreadsheet;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class WorksheetFactory {
    private static SimpleDateFormat ISO8601_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private WorksheetFactory() {
        throw new AssertionError("Don't call constructor");
    }

    public static List<Worksheet> getWorksheets(String spreadsheetId, String authToken) throws XmlPullParserException, IOException {
        String worksheetAddress = "https://spreadsheets.google.com/feeds/worksheets/" + spreadsheetId + "/private/full?access_token=" + authToken;
        URL url = new URL(worksheetAddress);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

        if (conn.getResponseCode() / 100 >= 3) {
            String s = new String(IOUtils.toByteArray(conn.getErrorStream()));
            throw new RuntimeException(s);
        }

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(new BufferedReader(new InputStreamReader(conn.getInputStream())));

        int eventType = xpp.getEventType();

        List<Worksheet> worksheetList = new ArrayList<Worksheet>();
        Worksheet worksheet = null;
        String lastTag = "";

        while (eventType != XmlPullParser.END_DOCUMENT) {
                
            if(eventType == XmlPullParser.START_DOCUMENT) {
            } else if(eventType == XmlPullParser.START_TAG) {
                lastTag = xpp.getName();
                if(xpp.getName().equals("entry")) {
                    worksheet = new Worksheet();
                }
            } else if(eventType == XmlPullParser.END_TAG) {
                if(xpp.getName().equals("entry")) {
                    worksheetList.add(worksheet);
                    worksheet = null;
                }
            } else if(eventType == XmlPullParser.TEXT) {
                if(worksheet != null && lastTag.equals("id")) {
                    worksheet.setId(DownloaderUtils.getLastPartFromUrl(xpp.getText()));
                }
                if(worksheet != null && lastTag.equals("title")) {
                    worksheet.setTitle(xpp.getText());
                }
                if(worksheet != null && lastTag.equals("updated")) {
                    try {
                        worksheet.setUpdateDate(ISO8601_FORMATTER.parse(xpp.getText()));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            }
            eventType = xpp.next();
        }
        return worksheetList;
    }

    public static void deleteWorksheet(Spreadsheet spreadsheet, Worksheet worksheet, String authToken) throws Exception {
        String requestUrl= "https://spreadsheets.google.com/feeds/worksheets/" + spreadsheet.getId() + "/private/full/" + worksheet.getId() + "/0" + "?access_token=" + authToken;
        URL url = new URL(requestUrl);
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.addRequestProperty("If-Match", "*");
        if (conn.getResponseCode() / 100 >= 3) {
            String s = new String(IOUtils.toByteArray(conn.getErrorStream()));
            throw new Exception(s);
        }
    }

    public static Worksheet createWorksheet(Spreadsheet spreadsheet, String title, int row, int col, String authToken) throws Exception {
        URL url = new URL("https://spreadsheets.google.com/feeds/worksheets/" + spreadsheet.getId() + "/private/full?access_token=" + authToken);

        String payload = "<entry xmlns=\"http://www.w3.org/2005/Atom\""
            + " xmlns:gs=\"http://schemas.google.com/spreadsheets/2006\">"
            + "<title>" + URLEncoder.encode(title, "UTF-8") + "</title>"
            + "<gs:rowCount>" + row + "</gs:rowCount>"
            + "<gs:colCount>" + col + "</gs:colCount>"
            + "</entry>";


        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoInput(true);
        conn.addRequestProperty("Content-Type", "application/atom+xml");
        conn.addRequestProperty("Content-Length", "" + payload.length());
        OutputStreamWriter out = new OutputStreamWriter(conn.getOutputStream());
        out.write(payload);
        out.close();

        if (conn.getResponseCode() / 100 >= 3) {
            String s = new String(IOUtils.toByteArray(conn.getErrorStream()));
            throw new Exception(s);
        }

        List<Worksheet> worksheets = getWorksheets(spreadsheet.getId(), authToken);
        for (Worksheet worksheet : worksheets) {
            if (title.equals(worksheet.getTitle())) {
                return worksheet;
            }
        }
        throw new IllegalStateException("Worksheet lookup failed. Worksheet is not created properly.");
    }

    public static List<Spreadsheet> findSpreadsheets(String title, String authToken) throws Exception {
        URL url = new URL("https://docs.google.com/feeds/default/private/full?title=" + URLEncoder.encode(title, "UTF-8") + "&title-exact=true&access_token=" + authToken);

        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.addRequestProperty("Authorization", "GoogleLogin auth=" + authToken);
        conn.addRequestProperty("GData-Version", "3.0");

        if (conn.getResponseCode() / 100 >= 3) {
            String s = new String(IOUtils.toByteArray(conn.getErrorStream()));
            throw new Exception(s);
        }

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(new BufferedReader(new InputStreamReader(conn.getInputStream())));

        int eventType = xpp.getEventType();

        List<Spreadsheet> spreadsheetList = new ArrayList<Spreadsheet>(5);
        Spreadsheet spreadsheet = null;
        String lastTag = "";
        while (eventType != XmlPullParser.END_DOCUMENT) {
                
            if(eventType == XmlPullParser.START_DOCUMENT) {
            } else if(eventType == XmlPullParser.START_TAG) {
                lastTag = xpp.getName();
                if(xpp.getName().equals("entry")) {
                    spreadsheet = new Spreadsheet();
                }
            } else if(eventType == XmlPullParser.END_TAG) {
                if(xpp.getName().equals("entry")) {
                    spreadsheetList.add(spreadsheet);
                    spreadsheet = null;
                }
            } else if(eventType == XmlPullParser.TEXT) {
                if(spreadsheet != null && lastTag.equals("id")) {
                    spreadsheet.setId(DownloaderUtils.getLastPartFromUrl(xpp.getText()));
                }
                if(spreadsheet != null && lastTag.equals("title")) {
                    spreadsheet.setTitle(xpp.getText());
                }
                if(spreadsheet != null && lastTag.equals("updated")) {
                    try {
                        spreadsheet.setUpdateDate(ISO8601_FORMATTER.parse(xpp.getText()));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            }
            eventType = xpp.next();
        }
        return spreadsheetList;

    }
}
