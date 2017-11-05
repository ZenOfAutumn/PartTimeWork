package com.zen.autumn.secfiles;

import com.google.common.collect.Sets;
import com.zen.autumn.secfiles.SecFileCrawler.FinalReportRow;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * File Processor
 **/
public class FileProcessor {


public  static  final  String FILE_ROOT = "D:\\TempWork\\";

  public static final Set<FinalReportRow> process(String file) {
    Set<FinalReportRow> rowSet = Sets.newHashSet();
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));

      String line = reader.readLine();
      while (StringUtils.isNotBlank(line)) {
        String[] parts = line.split("\t");
        if (parts[0].length() < "0000001750".length()) {
          parts[0] = "000000" + parts[0];
        }
        FinalReportRow row = new FinalReportRow();
        row.setCIK(parts[0]);
        row.setPeriodYear(parts[1]);
        rowSet.add(row);
        line = reader.readLine();
      }
      reader.close();
      return  rowSet;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return  null;
  }

  public static void main(String[] args) {
    System.out.println(process("D:\\TempWork\\source_modify").size());
  }

}
