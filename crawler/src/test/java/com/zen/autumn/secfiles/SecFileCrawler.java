package com.zen.autumn.secfiles;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.zen.autumn.httpclient.HttpClientUtil;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.filters.CssSelectorNodeFilter;
import org.htmlparser.filters.NodeClassFilter;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.htmlparser.util.SimpleNodeIterator;

/**
 * Sec Files Crawler
 **/
public class SecFileCrawler {

  static final String ROOT_URL = "https://www.sec.gov/cgi-bin/browse-edgar";
  static final String ROOT = "https://www.sec.gov";


  // class="tableFile2"
  static final String DEF14A_TABLE_CLASS = ".tableFile2";

  // id = "documentsbutton"
  static final String DEF14A_HREF_ID = "#documentsbutton";

  // SEC Website WebRoot
  static final String SEC_ROOT = "https://www.sec.gov";

  static final int MAX_YEAR = 2016;

  static final int MIN_YEAR = 2011;

  static final String INFO_HEAD = ".infoHead";

  static final String FILING_DATE = "Filing Date";

  static final String PERIOD_REPORT = "Period of Report";

  static final String RANGE = "201X-07-01-201[X+1]-06-31 -201X";

  static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

  static final String SUFFIX = "23:59:59";

  public static final String getPage(String url) {
    return HttpClientUtil.doGet(url, null, "utf-8");
  }

  public static final FinalReportRow parse(String html) {
    Parser parser = new Parser(new Lexer(html));
    FinalReportRow row = new FinalReportRow();

    try {
      NodeList nodeList = parser.extractAllNodesThatMatch(new CssSelectorNodeFilter(INFO_HEAD));
      SimpleNodeIterator iterator = nodeList.elements();
      while (iterator.hasMoreNodes()) {
        Node node = iterator.nextNode();
        if (node.toPlainTextString().equals(PERIOD_REPORT)) {
          Node date = node.getNextSibling().getNextSibling();
          row.setPeriodYear(date.toPlainTextString());
        }
        if (node.toPlainTextString().equals(FILING_DATE)) {
          Node date = node.getNextSibling().getNextSibling();
          row.setFilingDate(date.toPlainTextString());
        }
      }

      Parser newParser = new Parser(new Lexer(html));

      NodeList links = newParser.extractAllNodesThatMatch(new NodeClassFilter(LinkTag.class));
      SimpleNodeIterator iterator1 = links.elements();
      while (iterator1.hasMoreNodes()) {
        Node next = iterator1.nextNode();
        LinkTag link = (LinkTag) next;
        String url = link.extractLink();
        if (url.contains(".htm") && url.contains("Archives/edgar/data")) {
          row.setFinalPageUrl(url);
          break;
        }
      }

      return row;
    } catch (ParserException e) {
      e.printStackTrace();
    }
    return null;
  }

  // 获取DEF14A列表页
  public static String getDEF14AListPage(String CIKCode) {

    Map<String, String> params = Maps.newHashMap();
    params.put("CIK", CIKCode);
    params.put("action", "getcompany");
    params.put("type", URLEncoder.encode("DEF 14A"));
    params.put("count", "100");

    String response = HttpClientUtil.doGet(ROOT_URL, params, "UTF-8");
    return response;

  }

  public static List<String> getDEF14Links(String html) {

    List<String> links = new ArrayList<String>();
    Lexer lexer = new Lexer(html);
    Parser parser = new Parser(lexer);
    try {
      NodeList nodeList = parser
        .extractAllNodesThatMatch(new CssSelectorNodeFilter(DEF14A_HREF_ID));
      SimpleNodeIterator iterator = nodeList.elements();
      while (iterator.hasMoreNodes()) {
        Node node = iterator.nextNode();
        LinkTag linkTag = (LinkTag) node;
        links.add(SEC_ROOT + linkTag.extractLink());
      }
      return links;
    } catch (ParserException e) {
      e.printStackTrace();
    }

    return null;
  }

  public static List<FinalReportRow> compose(boolean out) {
    BufferedWriter errorWriter = null;
    BufferedWriter errorUrlWriter = null;
    BufferedWriter resultWriter = null;

    try {
      errorWriter = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream("D:\\TempWork\\error_CIK")));
      errorUrlWriter = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream("D:\\TempWork\\error_URL")));
      resultWriter = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream("D:\\TempWork\\result_first_step")));

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    Set<FinalReportRow> all = FileProcessor.process("D:\\TempWork\\source_modify");
    Set<String> CIKCodeSet = Sets.newHashSet(Collections2
      .transform(all, new Function<FinalReportRow, String>() {
        @Nullable
        public String apply(@Nullable FinalReportRow input) {
          return input.getCIK();
        }
      }));

    List<FinalReportRow> result = Lists.newArrayList();

    for (String CIKCode : CIKCodeSet) {
      Long start = System.currentTimeMillis();
      String DEF14APage = getDEF14AListPage(CIKCode);

      if (DEF14APage == null) {
        try {
          errorWriter.write(CIKCode);
          errorWriter.newLine();
          continue;
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

      List<String> DEF14Links = getDEF14Links(DEF14APage);
      for (String def14Link : DEF14Links) {
        String html = getPage(def14Link);
        if (html == null) {
          try {
            errorUrlWriter.write(def14Link);
            errorUrlWriter.newLine();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
        FinalReportRow row = parse(html);
        row.setCIK(CIKCode);
        String periodDate = row.getPeriodYear();
        Long actualYear = extractYear(periodDate);
        row.setPeriodYear(actualYear.toString());
        if (all.contains(row)) {
          result.add(row);

          try {
            resultWriter.write(StringUtils.join(
              new Object[]{row.getCIK(), row.getPeriodYear(), row.getFilingDate(),
                ROOT+row.getFinalPageUrl()}, ","));
            resultWriter.newLine();
            resultWriter.flush();

          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }

      Long end = System.currentTimeMillis();
      System.out.println("CIKCode: " + CIKCode + " Time: " + (end - start));
    }

    try {
      errorUrlWriter.close();
      errorWriter.close();
      resultWriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    return result;
  }

  public static final Long extractYear(String date) {
    String year = date.split("-")[0];
    try {
      Date origin = DateUtils.parseDate(date + " " + SUFFIX, DATE_PATTERN);
      Date splitDate = DateUtils.parseDate(year + "-06-30" + " " + SUFFIX, DATE_PATTERN);
      if (origin.after(splitDate)) {
        return Long.parseLong(year) + 1;
      } else {
        return Long.parseLong(year);
      }
    } catch (ParseException e) {
      e.printStackTrace();
    }
    return null;
  }

  public static void main(String[] args) {

    compose(true);

  }

  public static class DateGroup {

    private String filingDate;

    private String periodDate;

    public String getFilingDate() {
      return filingDate;
    }

    public void setFilingDate(String filingDate) {
      this.filingDate = filingDate;
    }

    public String getPeriodDate() {
      return periodDate;
    }

    public void setPeriodDate(String periodDate) {
      this.periodDate = periodDate;
    }
  }

  public static class FinalReportRow {

    private String company;

    private String CIK;

    private String periodYear;

    private String filingDate;

    private String nameOfOwner;

    private String address;

    private String shareNumber;

    private String sharePercent;

    private String footNote;

    private String page;

    private String finalPageUrl;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      FinalReportRow row = (FinalReportRow) o;

      if (!CIK.equals(row.CIK)) {
        return false;
      }
      return periodYear.equals(row.periodYear);
    }

    @Override
    public int hashCode() {
      int result = CIK.hashCode();
      result = 31 * result + periodYear.hashCode();
      return result;
    }

    public String getFinalPageUrl() {
      return finalPageUrl;
    }

    public void setFinalPageUrl(String finalPageUrl) {
      this.finalPageUrl = finalPageUrl;
    }

    public String getPage() {
      return page;
    }

    public void setPage(String page) {
      this.page = page;
    }

    public String getCompany() {
      return company;
    }

    public void setCompany(String company) {
      this.company = company;
    }

    public String getCIK() {
      return CIK;
    }

    public void setCIK(String CIK) {
      this.CIK = CIK;
    }

    public String getPeriodYear() {
      return periodYear;
    }

    public void setPeriodYear(String periodYear) {
      this.periodYear = periodYear;
    }

    public String getFilingDate() {
      return filingDate;
    }

    public void setFilingDate(String filingDate) {
      this.filingDate = filingDate;
    }

    public String getNameOfOwner() {
      return nameOfOwner;
    }

    public void setNameOfOwner(String nameOfOwner) {
      this.nameOfOwner = nameOfOwner;
    }

    public String getAddress() {
      return address;
    }

    public void setAddress(String address) {
      this.address = address;
    }

    public String getShareNumber() {
      return shareNumber;
    }

    public void setShareNumber(String shareNumber) {
      this.shareNumber = shareNumber;
    }

    public String getSharePercent() {
      return sharePercent;
    }

    public void setSharePercent(String sharePercent) {
      this.sharePercent = sharePercent;
    }

    public String getFootNote() {
      return footNote;
    }

    public void setFootNote(String footNote) {
      this.footNote = footNote;
    }
  }

}
