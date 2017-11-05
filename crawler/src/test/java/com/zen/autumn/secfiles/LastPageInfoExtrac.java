package com.zen.autumn.secfiles;

import com.google.common.collect.Sets;
import com.zen.autumn.httpclient.HttpClientUtil;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.LinkedHashSet;
import org.apache.commons.lang3.StringUtils;
import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Parser;
import org.htmlparser.filters.AndFilter;
import org.htmlparser.filters.TagNameFilter;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.htmlparser.util.SimpleNodeIterator;

/**
 * Last Page Info Extrac
 **/
public class LastPageInfoExtrac {

  static final LinkedHashSet<String> KEYWORD = Sets.newLinkedHashSet();

  static final LinkedHashSet<String> BIG_COM = Sets.newLinkedHashSet();

  static final String ROOT = "D:\\TempWork\\";

  static final String SUFFIX = ".htm";

  static {
    KEYWORD.add("CERTAIN BENEFICIAL OWNERS");
    KEYWORD.add("Certain Beneficial Owners");
    KEYWORD.add("BENEFICIAL OWNER");
    KEYWORD.add("BENEFICIAL OWNER".toLowerCase());
    KEYWORD.add("Beneficial Owner");
    KEYWORD.add("BENEFICIAL OWNERSHIP");
    KEYWORD.add("BENEFICIAL OWNERSHIP".toLowerCase());
    KEYWORD.add("Beneficial Ownership");

    BIG_COM.add("BlackRock");
    BIG_COM.add("Blackrock");
    BIG_COM.add("Black Rock");
    BIG_COM.add("5% Stockholders");
    BIG_COM.add("Vanguard");
    BIG_COM.add("Beneficial Owner");
    BIG_COM.add("Lightyear Capital");



//    KEYWORD.add("BENEFICIAL");
//    KEYWORD.add("BENEFICIAL".toLowerCase());
//    KEYWORD.add("OWNER");
//    KEYWORD.add("OWNER".toLowerCase());
  }

  public static boolean testCom(String url) {
    String html = HttpClientUtil.doGet(url, null, "UTF-8");
    for (String key : BIG_COM) {
      if (html.contains(key)) {
        return true;
      }
    }
    return false;
  }

  public static void outerTestCom() {
    try {
      BufferedReader reader = new BufferedReader(
        new InputStreamReader(new FileInputStream(FileProcessor.FILE_ROOT + "fisrt_step_exam")));
      String line = reader.readLine();
      while (StringUtils.isNotBlank(line)) {
        String[] content = line.split(",");
        String url = content[3];
        if (!testCom(url)) {
          System.out.println(url);
        } else {
          System.out.println(true);
        }
      }
    } catch (IOException e) {

    }
  }

  public static void extractCorePage() {
    try {
      BufferedReader reader = new BufferedReader(
        new InputStreamReader(new FileInputStream(FileProcessor.FILE_ROOT + "fisrt_step_exam")));
      String line = reader.readLine();
      while (StringUtils.isNotBlank(line)) {
        getCorePage(line);
        line = reader.readLine();
      }
    } catch (IOException e) {

    }
  }

  public static final Node getCorrectLevel(Node node) {
    Node parent = node;
    while (true) {
      String content = parent.toHtml();
      if (content.contains("<text") || content.contains("<TEXT") || content
        .contains("<BODY") || content.contains("<body")) {
        return parent;
      } else {
        parent = parent.getParent();
      }
    }
  }

  public static void getCorePage(String line) {
    String[] content = line.split(",");
    String CIK = content[0];
    String year = content[1];
    String url = content[3];

    String html = HttpClientUtil.doGet(url, null, "UTF-8");
    if(StringUtils.isBlank(html)){
      System.out.println("blank: " + url);
    }
    Parser parser = new Parser(new Lexer(html));
    try {

      NodeFilter contentFilter = new NodeFilter() {
        public boolean accept(Node node) {
          for (String big : BIG_COM) {
            if (node.toHtml().contains(big)) {
              return true;
            }
          }
          return false;
        }
      };
      NodeList list = parser.extractAllNodesThatMatch(
        new AndFilter(new NodeFilter[]{new TagNameFilter("table"), contentFilter}));
      SimpleNodeIterator iterator = list.elements();
      if(!iterator.hasMoreNodes()){
        System.out.println(url);
        return;
      }

      Node table = getCorrectLevel(iterator.nextNode());
      StringBuilder str = new StringBuilder();
      str.append(table.toHtml());
      int HR_LIMIT = 4;
      int i = 0;
      Node sibling = table;
      while (true) {
        sibling = sibling.getNextSibling();
        if (sibling == null) {
          break;
        }
        String part = sibling.toHtml();
        str.append(part);
        if (part.contains("<hr") || part.contains("<HR")) {
          i++;
          if (i >= HR_LIMIT) {
            break;
          }
        }
      }

      BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(ROOT + CIK + "_" + year + "_temp" + SUFFIX)));
      writer.write(str.toString());
      writer.close();
    } catch (ParserException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    extractCorePage();
  }

}
