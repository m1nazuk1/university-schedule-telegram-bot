package io.proj3ct.KFBaumanScheduleBot.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

class ExcelRead {
    public static void main(String[] args) throws IOException {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(new Date());
        if (calendar.get(Calendar.WEEK_OF_MONTH) % 2 != 0){
            System.out.println("знаменатель");
        }else {
            System.out.println("числитель");
        }
//        Map<Integer, List<String>> data = new HashMap<>();
//        int i = 0;
//        for (Row row : sheet) {
//            data.put(i, new ArrayList<String>());
//            for (Cell cell : row) {
//                switch (cell.getCellType()) {
//                    case STRING:
//                        data.get(i).add(cell.getRichStringCellValue().getString());
//                        break;
//                    case NUMERIC:
//                        break;
//                    case BOOLEAN:
//                        break;
//                    case FORMULA:
//                        break;
//                    default: data.get(i).add(" ");
//                }
//            }
//            i++;
//        }
//        System.out.println(data.toString());
//    }
    }
}