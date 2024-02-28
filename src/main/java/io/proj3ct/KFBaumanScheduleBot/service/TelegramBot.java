package io.proj3ct.KFBaumanScheduleBot.service;

import com.vdurmont.emoji.EmojiParser;
import io.proj3ct.KFBaumanScheduleBot.config.BotConfig;
import io.proj3ct.KFBaumanScheduleBot.model.User;
import io.proj3ct.KFBaumanScheduleBot.model.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private List<String> arr = new ArrayList<>();
    private String filePath = "kf.xls";
    private String url = "jdbc:mysql://localhost:3306/jdbc";
    private String user = "root";
    private String password = "Haker-777";
    private int myCourse;
    private StringBuilder shedulee = new StringBuilder();

    @Autowired
    private UserRepository userRepository;
    final BotConfig config;
    static final String HELP_TEXT = "Автор этого бота - Величайший программист нашего столетия\n\nАлекперов Низами :)\n\nОн сделан для удобного просмотра расписания в КФ МГТУ им Н.Э. Баумана, но пока что всё находится в разработке. " +
            "\n\n\nВот доступные команды ( вы можете просмотреть их в моем гениальном меню )\n\n" +
            "/start - начало работы, бот просит вас ввести данные о вашем курсе и кафедре\n\n" +
            "/schedule - основываясь на ваших данных, бот направляет вам расписание\n\n" +
            "/register - вы можете поменять свою группу и курс, после этого сможете быстро просматривать расписание\n\n";
//           + "/mydata - тут вы можете наблюдать информацию о вашем профиле, которую имеет бот в данный момент времени\n\n" +
//            "/deletedata - с помощью этой команды вы можете удалить данные о вашем профиле из базы данных бота\n\n" +
//            "/settings - эта команда позволяет изменить определенные настройки бота для более удобного пользования";

    public TelegramBot(BotConfig config){
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "начало работы"));
        listOfCommands.add(new BotCommand("/schedule", "запросить расписание пар"));
        listOfCommands.add(new BotCommand("/register", "зарегистрироваться"));
//        listOfCommands.add(new BotCommand("/mydata", "запросить информацию о своем профиле"));
//        listOfCommands.add(new BotCommand("/deletedata", "удалить информацию о своем профиле"));
        listOfCommands.add(new BotCommand("/help", "помощь"));
//        listOfCommands.add(new BotCommand("/settings", "настройки"));
        try{
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        }catch (TelegramApiException e){
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    private Chat chatik;
    private Long localChatId;
    private String weeks;
    private String days;

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()){

            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            log.info("User:" + update.getMessage().getChat().getFirstName() + "  Message: " + messageText);
            switch (messageText){
                case "/start":
                    chatik = update.getMessage().getChat();
                    localChatId = chatId;
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                    register(chatId);
                    break;
                case "/help":
                    sendMessage(chatId, HELP_TEXT);
                    break;
                case "/schedule":
                    scheduleEx(chatId, update.getMessage().getMessageId());
                    break;
                case "/register":
                    chatik = update.getMessage().getChat();
                    localChatId = chatId;
                    info(chatId);
                    break;
                default:
                    sendMessage(chatId, EmojiParser.parseToUnicode("я пока что тупой, таких команд не знаю\nдля просмотра списка команд введите /help или выберите эту команду в вспомогательном меню, удачи!" + "\uD83E\uDEE0"));
            }
        }else if (update.hasCallbackQuery()){
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.equals("STUDENT_BUTTON"))
            {
                choiceCourse(chatId, (int) messageId);

            }else if (callbackData.equals("TEACHER_BUTTON")){
                EditMessageText message = new EditMessageText();
                message.setChatId(String.valueOf(chatId));
                message.setText(EmojiParser.parseToUnicode("я еще делаю((( " + "\uD83D\uDC80"));
                message.setMessageId((int) messageId);
                try {
                    execute(message);
                }catch (TelegramApiException e){
                    log.error("Error occurred: " + e.getMessage());
                }

            } else if (callbackData.equals("FIRST")) {
                EditMessageText message = new EditMessageText();
                message.setChatId(String.valueOf(chatId));
                message.setText(EmojiParser.parseToUnicode("мне не выдали расписание для 1 курса, поэтому извините..." + "\uD83D\uDC80"));
                message.setMessageId((int) messageId);
                try {
                    execute(message);
                }catch (TelegramApiException e){
                    log.error("Error occurred: " + e.getMessage());
                }
            } else if (callbackData.equals("SECOND")){
                try {
                    choiceDepartamentForCourse(chatId, (int)messageId, 2);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            } else if (callbackData.equals("THIRD")){
                try {
                    choiceDepartamentForCourse(chatId, (int)messageId, 3);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }else if (callbackData.equals("FOURTH")){
                try {
                    choiceDepartamentForCourse(chatId, (int)messageId, 4);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }else if (callbackData.equals("FIFTH")){
                try {
                    choiceDepartamentForCourse(chatId, (int)messageId, 5);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }else if (callbackData.equals("SIXTH")){
                try {
                    choiceDepartamentForCourse(chatId, (int)messageId, 6);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }else if (callbackData.equals("MGFIRST")){
                try {
                    choiceDepartamentForCourse(chatId, (int)messageId, 7);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }else if (callbackData.equals("MGSECOND")){
                try {
                    choiceDepartamentForCourse(chatId, (int)messageId, 8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
            else if (arr.contains(callbackData)){
                registerUser(localChatId, chatik, String.valueOf(myCourse), callbackData);
                completedRegistration(localChatId, (int) messageId);

            }else if (callbackData.equals("YES_INFO")) {
                registerNew(chatId,(int) messageId);

            } else if (callbackData.equals("NO_INFO")) {
                EditMessageText message = new EditMessageText();
                message.setChatId(String.valueOf(chatId));
                message.setText(EmojiParser.parseToUnicode("анулан " + "\uD83D\uDC80"));
                message.setMessageId((int) messageId);
                try {
                    execute(message);
                }catch (TelegramApiException e){
                    log.error("Error occurred: " + e.getMessage());
                }
            }else if(callbackData.equals("TODAY")){
                days = "today";
                schedule(chatId, days);
                sendMessage(chatId, shedulee.toString());
            } else if (callbackData.equals("TOMORROW")) {
                days = "tomorrow";
                schedule(chatId, days);
                sendMessage(chatId, shedulee.toString());
            } else if (callbackData.equals("ALL_NOW")) {
                days = "allNow";
                schedule(chatId, days);
                sendMessage(chatId, shedulee.toString());
            } else if (callbackData.equals("ALL")) {
                days = "all";
                schedule(chatId, days);
                sendMessage(chatId, shedulee.toString());
            }
        }
    }

    private void scheduleEx(Long chatId, int messageId){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(EmojiParser.parseToUnicode("выберите нужное вам расписание"));
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        List<InlineKeyboardButton> secondRowInline = new ArrayList<>();
        List<InlineKeyboardButton> thirdRowInline = new ArrayList<>();
        List<InlineKeyboardButton> fourthRowInline = new ArrayList<>();
        var todButton = new InlineKeyboardButton();

        todButton.setText("на сегодня");
        todButton.setCallbackData("TODAY");

        var tomButton = new InlineKeyboardButton();

        tomButton.setText("на завтра");
        tomButton.setCallbackData("TOMORROW");

        var allNowButton = new InlineKeyboardButton();

        allNowButton.setText("на текущую неделю");
        allNowButton.setCallbackData("ALL_NOW");

        var allButton = new InlineKeyboardButton();

        allButton.setText("на всю неделю");
        allButton.setCallbackData("ALL");


        rowInline.add(todButton);
        secondRowInline.add(tomButton);
        thirdRowInline.add(allNowButton);
        fourthRowInline.add(allButton);
        rowsInline.add(rowInline);
        rowsInline.add(secondRowInline);
        rowsInline.add(thirdRowInline);
        rowsInline.add(fourthRowInline);

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
        try {
            execute(message);
        }catch (TelegramApiException e){
            log.error("Error occurred: " + e.getMessage());
        }

    }

    private void schedule(Long chatId,String whatSchedule){
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(new Date());
        if (calendar.get(Calendar.WEEK_OF_MONTH) % 2 != 0){
            weeks = "знаменатель";
        }else {
            weeks = "числитель";
        }



        java.util.Date z = new java.util.Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE", Locale.US);
        String str = dateFormat.format(z);

        int d = 1;
        try {
            if (whatSchedule.equals("all")){
                d = 0;
            }
            Connection connection = DriverManager.getConnection(url, user, password);
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT course FROM `tg-bot`.users_data_table WHERE chat_id = '" + chatId + "'");
            resultSet.next();
            resultSet.getString("course").toString();
            String course = resultSet.getString("course");

            ResultSet resultSet2 = statement.executeQuery("SELECT department FROM `tg-bot`.users_data_table WHERE chat_id = '" + chatId + "'");
            resultSet2.next();
            resultSet2.getString("department").toString();
            String department = resultSet2.getString("department");

            FileInputStream file = new FileInputStream(new File(filePath));
            Workbook workbook = new HSSFWorkbook(file);
            String result;
            String day;
            String nextResult = null;
            String time;


            switch (str){
                case "Mon":
                    str = "Понедельник";
                    break;
                case "Tue":
                    str = "Вторник";
                    break;
                case "Wed":
                    str = "Среда";
                    break;
                case "Thu":
                    str = "Четверг";
                    break;
                case "Fri":
                    str = "Пятница";
                    break;
                case "Sat":
                    str = "Суббота";
                    break;
                case "Sun":
                    str = "Воскресенье";
                    break;
            }


            List<String> nedelya = new ArrayList<>();
            switch (whatSchedule){
                case "today":
                    nedelya.add(str);
                    break;
                case "tomorrow":
                    switch (str){
                        case "Понедельник":
                            nedelya.add("Вторник");
                            break;
                        case "Вторник":
                            nedelya.add("Среда");
                            break;
                        case "Среда":
                            nedelya.add("Четверг");
                            break;
                        case "Четверг":
                            nedelya.add("Пятница");
                            break;
                        case "Пятница":
                            nedelya.add("Суббота");
                            break;
                        case "Суббота":
                            nedelya.add("Понедельник");
                            break;
                        case "Воскресенье":
                            nedelya.add("Понедельник");
                            break;
                    }
                    break;
                case "allNow":
                    nedelya.add("Понедельник");
                    nedelya.add("Вторник");
                    nedelya.add("Среда");
                    nedelya.add("Четверг");
                    nedelya.add("Пятница");
                    nedelya.add("Суббота");
                    break;
                case "all":
                    nedelya.add("Понедельник");
                    nedelya.add("Вторник");
                    nedelya.add("Среда");
                    nedelya.add("Четверг");
                    nedelya.add("Пятница");
                    nedelya.add("Суббота");
                    break;
                default: break;

            }

            StringBuilder sched = new StringBuilder();
            int a = Integer.parseInt(course);
            for (int i = 2; i < 100; i++){
                result = workbook.getSheetAt(a-1).getRow(0).getCell(i).getStringCellValue();
                if (result.equals(department)){
                    for (int k = 1; k < 100; k++){
                        result = workbook.getSheetAt(a-1).getRow(k).getCell(i).getStringCellValue();
                        if (result.equals(department)){
                            shedulee = sched;
                            return;
                        }
                        day = workbook.getSheetAt(a-1).getRow(k).getCell(0).getStringCellValue();
                        time = workbook.getSheetAt(a-1).getRow(k).getCell(1).getStringCellValue().replaceAll("\n", " ");
                        String nextDay;
                        if (day.equals("Понедельник") && nedelya.contains(day)){
                            day = "\n\n--------\n" + day + "\n--------\n";
                            sched.append(day);
                            while (true){
                                nextResult = workbook.getSheetAt(a-1).getRow(k+1).getCell(i).getStringCellValue();
                                if (!result.equals("")){
                                    if (time.equals("")){
                                        time =  workbook.getSheetAt(a-1).getRow(k-1).getCell(1).getStringCellValue().replaceAll("\n", " ") + " знаменатель";
                                    } else if (!time.equals("")) {
                                        if ((nextResult.equals(""))){
                                            if (!result.contains("I")){
                                                if (result.contains("лаб.") || result.contains("упр.")){
                                                    if (!result.contains("ИНО")) {
                                                        time = time + " числитель";
                                                    }else {
                                                        time = time;
                                                    }
                                                }else {
                                                    time = time;
                                                }
                                            }else {
                                                time = time;
                                            }
                                        }else {
                                            time = time + " числитель";
                                        }
                                    }
                                    if (d == 1) {
                                        if (time.contains("числитель") || time.contains("знаменатель")) {
                                            if (time.contains(weeks)) {
                                                sched.append("\n[" + time + "]\n" + result + "\n");
                                            }
                                        } else {
                                            sched.append("\n[" + time + "]\n" + result + "\n");
                                        }
                                    }else {
                                        sched.append("\n[" + time + "]\n" + result + "\n");

                                    }
                                }
                                k++;
                                result = workbook.getSheetAt(a-1).getRow(k).getCell(i).getStringCellValue();
                                time = workbook.getSheetAt(a-1).getRow(k).getCell(1).getStringCellValue().replaceAll("\n", " ");
                                nextDay = workbook.getSheetAt(a-1).getRow(k).getCell(0).getStringCellValue();
                                if (nextDay.equals("Вторник")){
                                    break;
                                }
                            }
                        }
                        day = workbook.getSheetAt(a-1).getRow(k).getCell(0).getStringCellValue();
                        if (day.equals("Вторник") && nedelya.contains(day)){
                            day = "\n\n--------\n" + day + "\n--------\n";
                            sched.append(day);
                            while (true){
                                nextResult = workbook.getSheetAt(a-1).getRow(k+1).getCell(i).getStringCellValue();
                                if (!result.equals("")){
                                    if (time.equals("")){
                                        time =  workbook.getSheetAt(a-1).getRow(k-1).getCell(1).getStringCellValue().replaceAll("\n", " ") + " знаменатель";
                                    } else if (!time.equals("")) {
                                        if ((nextResult.equals(""))){
                                            if (!result.contains("I")){
                                                if (result.contains("лаб.") || result.contains("упр.")){
                                                    if (!result.contains("ИНО")) {
                                                        time = time + " числитель";
                                                    }else {
                                                        time = time;
                                                    }
                                                }else {
                                                    time = time;
                                                }
                                            }else {
                                                time = time;
                                            }
                                        }else {
                                            time = time + " числитель";
                                        }
                                    }
                                    if (d == 1) {
                                        if (time.contains("числитель") || time.contains("знаменатель")) {
                                            if (time.contains(weeks)) {
                                                sched.append("\n[" + time + "]\n" + result + "\n");
                                            }
                                        } else {
                                            sched.append("\n[" + time + "]\n" + result + "\n");
                                        }
                                    }else {
                                        sched.append("\n[" + time + "]\n" + result + "\n");

                                    }
                                }
                                k++;
                                result = workbook.getSheetAt(a-1).getRow(k).getCell(i).getStringCellValue();
                                time = workbook.getSheetAt(a-1).getRow(k).getCell(1).getStringCellValue().replaceAll("\n", " ");
                                nextDay = workbook.getSheetAt(a-1).getRow(k).getCell(0).getStringCellValue();
                                if (nextDay.equals("Среда")){
                                    break;
                                }
                            }

                        }
                        day = workbook.getSheetAt(a-1).getRow(k).getCell(0).getStringCellValue();
                        if (day.equals("Среда") && nedelya.contains(day)){
                            day = "\n\n--------\n" + day + "\n--------\n";
                            sched.append(day);
                            while (true){
                                nextResult = workbook.getSheetAt(a-1).getRow(k+1).getCell(i).getStringCellValue();
                                if (!result.equals("")){
                                    if (time.equals("")){
                                        time =  workbook.getSheetAt(a-1).getRow(k-1).getCell(1).getStringCellValue().replaceAll("\n", " ") + " знаменатель";
                                    } else if (!time.equals("")) {
                                        if ((nextResult.equals(""))){
                                            if (!result.contains("I")){
                                                if (result.contains("лаб.") || result.contains("упр.")){
                                                    if (!result.contains("ИНО")) {
                                                        time = time + " числитель";
                                                    }else {
                                                        time = time;
                                                    }
                                                }else {
                                                    time = time;
                                                }
                                            }else {
                                                time = time;
                                            }
                                        }else {
                                            time = time + " числитель";
                                        }
                                    }
                                    if (d == 1) {
                                        if (time.contains("числитель") || time.contains("знаменатель")) {
                                            if (time.contains(weeks)) {
                                                sched.append("\n[" + time + "]\n" + result + "\n");
                                            }
                                        } else {
                                            sched.append("\n[" + time + "]\n" + result + "\n");
                                        }
                                    }else {
                                        sched.append("\n[" + time + "]\n" + result + "\n");

                                    }
                                }
                                k++;
                                result = workbook.getSheetAt(a-1).getRow(k).getCell(i).getStringCellValue();
                                time = workbook.getSheetAt(a-1).getRow(k).getCell(1).getStringCellValue().replaceAll("\n", " ");
                                nextDay = workbook.getSheetAt(a-1).getRow(k).getCell(0).getStringCellValue();
                                if (nextDay.equals("Четверг")){
                                    break;
                                }
                            }

                        }
                        day = workbook.getSheetAt(a-1).getRow(k).getCell(0).getStringCellValue();

                        if (day.equals("Четверг") && nedelya.contains(day)) {
                            day = "\n\n--------\n" + day + "\n--------\n";
                            sched.append(day);
                            while (true){
                                nextResult = workbook.getSheetAt(a-1).getRow(k+1).getCell(i).getStringCellValue();
                                if (!result.equals("")){
                                    if (time.equals("")){
                                        time =  workbook.getSheetAt(a-1).getRow(k-1).getCell(1).getStringCellValue().replaceAll("\n", " ") + " знаменатель";
                                    } else if (!time.equals("")) {
                                        if ((nextResult.equals(""))){
                                            if (!result.contains("I")){
                                                if (result.contains("лаб.") || result.contains("упр.")){
                                                    if (!result.contains("ИНО")) {
                                                        time = time + " числитель";
                                                    }else {
                                                        time = time;
                                                    }
                                                }else {
                                                    time = time;
                                                }
                                            }else {
                                                time = time;
                                            }
                                        }else {
                                            time = time + " числитель";
                                        }
                                    }
                                    if (d == 1) {
                                        if (time.contains("числитель") || time.contains("знаменатель")) {
                                            if (time.contains(weeks)) {
                                                sched.append("\n[" + time + "]\n" + result + "\n");
                                            }
                                        } else {
                                            sched.append("\n[" + time + "]\n" + result + "\n");
                                        }
                                    }else {
                                        sched.append("\n[" + time + "]\n" + result + "\n");

                                    }
                                }
                                k++;
                                result = workbook.getSheetAt(a-1).getRow(k).getCell(i).getStringCellValue();
                                time = workbook.getSheetAt(a-1).getRow(k).getCell(1).getStringCellValue().replaceAll("\n", " ");
                                nextDay = workbook.getSheetAt(a-1).getRow(k).getCell(0).getStringCellValue();
                                if (nextDay.equals("Пятница")){
                                    break;
                                }
                            }
                        }
                        day = workbook.getSheetAt(a-1).getRow(k).getCell(0).getStringCellValue();

                        if (day.equals("Пятница") && nedelya.contains(day)){

                            day = "\n\n--------\n" + day + "\n--------\n";
                            sched.append(day);
                            while (true){
                                nextResult = workbook.getSheetAt(a-1).getRow(k+1).getCell(i).getStringCellValue();

                                if (!result.equals("")){
                                    if (time.equals("")){
                                        time =  workbook.getSheetAt(a-1).getRow(k-1).getCell(1).getStringCellValue().replaceAll("\n", " ") + " знаменатель";
                                    } else if (!time.equals("")) {
                                        if ((nextResult.equals(""))){
                                            if (!result.contains("I")){
                                                if (result.contains("лаб.") || result.contains("упр.")){
                                                    if (!result.contains("ИНО")) {
                                                        time = time + " числитель";
                                                    }else {
                                                        time = time;
                                                    }
                                                }else {
                                                    time = time;
                                                }
                                            }else {
                                                time = time;
                                            }
                                        }else {
                                            time = time + " числитель";
                                        }
                                    }
                                    if (d == 1) {
                                        if (time.contains("числитель") || time.contains("знаменатель")) {
                                            if (time.contains(weeks)) {
                                                sched.append("\n[" + time + "]\n" + result + "\n");
                                            }
                                        } else {
                                            sched.append("\n[" + time + "]\n" + result + "\n");
                                        }
                                    }else {
                                        sched.append("\n[" + time + "]\n" + result + "\n");

                                    }
                                }
                                k++;
                                result = workbook.getSheetAt(a-1).getRow(k).getCell(i).getStringCellValue();
                                time = workbook.getSheetAt(a-1).getRow(k).getCell(1).getStringCellValue().replaceAll("\n", " ");
                                nextDay = workbook.getSheetAt(a-1).getRow(k).getCell(0).getStringCellValue();
                                if (nextDay.equals("Суббота")){
                                    break;
                                }
                            }

                        }
                        day = workbook.getSheetAt(a-1).getRow(k).getCell(0).getStringCellValue();

                        if (day.equals("Суббота") && nedelya.contains(day)){

                            day = "\n\n--------\n" + day + "\n--------\n";
                            sched.append(day);
                            while (true){
                                nextResult = workbook.getSheetAt(a-1).getRow(k+1).getCell(i).getStringCellValue();

                                if (!result.equals("")){
                                    if (time.equals("")){
                                        time =  workbook.getSheetAt(a-1).getRow(k-1).getCell(1).getStringCellValue().replaceAll("\n", " ") + " знаменатель";
                                    } else if (!time.equals("")) {
                                        if ((nextResult.equals(""))){
                                            if (!result.contains("I")){
                                                if (result.contains("лаб.") || result.contains("упр.")){
                                                    if (!result.contains("ИНО")) {
                                                        time = time + " числитель";
                                                    }else {
                                                        time = time;
                                                    }
                                                }else {
                                                    time = time;
                                                }
                                            }else {
                                                time = time;
                                            }
                                        }else {
                                            time = time + " числитель";
                                        }
                                    }
                                    if (d == 1) {
                                        if (time.contains("числитель") || time.contains("знаменатель")) {
                                            if (time.contains(weeks)) {
                                                sched.append("\n[" + time + "]\n" + result + "\n");
                                            }
                                        } else {
                                            sched.append("\n[" + time + "]\n" + result + "\n");
                                        }
                                    }else {
                                        sched.append("\n[" + time + "]\n" + result + "\n");

                                    }
                                }
                                k++;
                                result = workbook.getSheetAt(a-1).getRow(k).getCell(i).getStringCellValue();
                                time = workbook.getSheetAt(a-1).getRow(k).getCell(1).getStringCellValue().replaceAll("\n", " ");
                                nextDay = workbook.getSheetAt(a-1).getRow(k).getCell(0).getStringCellValue();
                                if (result.equals(department)){
                                    shedulee = sched;
                                    return;
                                }
                            }

                        }
                    }
                }
            }

        } catch (SQLException e) {
            log.error("Error: " + e.getMessage());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void info(long chatId){
        try {
            Connection connection = DriverManager.getConnection(url, user, password);
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT course FROM `tg-bot`.users_data_table WHERE chat_id = '" + chatId +"'");
            resultSet.next();
            resultSet.getString("course").toString();
            String course = resultSet.getString("course");

            ResultSet resultSet2 = statement.executeQuery("SELECT department FROM `tg-bot`.users_data_table WHERE chat_id = '"+ chatId +"'");
            resultSet2.next();
            resultSet2.getString("department").toString();
            String department = resultSet2.getString("department");

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(EmojiParser.parseToUnicode("Вы на " + course + " курсе" + "\nНа кафедре: " + department + "\nхотите изменить данные?"));
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            List<InlineKeyboardButton> rowInline = new ArrayList<>();
            List<InlineKeyboardButton> secondRowInline = new ArrayList<>();
            var yesButton = new InlineKeyboardButton();

            yesButton.setText("Да");
            yesButton.setCallbackData("YES_INFO");

            var noButton = new InlineKeyboardButton();

            noButton.setText("Нет");
            noButton.setCallbackData("NO_INFO");

            rowInline.add(yesButton);
            secondRowInline.add(noButton);

            rowsInline.add(rowInline);
            rowsInline.add(secondRowInline);

            markupInline.setKeyboard(rowsInline);
            message.setReplyMarkup(markupInline);
            try {
                execute(message);
            }catch (TelegramApiException e){
                log.error("Error occurred: " + e.getMessage());
            }
        } catch (SQLException e) {
            log.error("Error: " + e.getMessage());
        }
    }

    private void completedRegistration(long chatId, int messageId){
        try {
            Connection connection = DriverManager.getConnection(url, user, password);
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT course FROM `tg-bot`.users_data_table WHERE chat_id = '" + chatId +"'");
            resultSet.next();
            resultSet.getString("course").toString();
            String course = resultSet.getString("course");

            ResultSet resultSet2 = statement.executeQuery("SELECT department FROM `tg-bot`.users_data_table WHERE chat_id = '"+ chatId +"'");
            resultSet2.next();
            resultSet2.getString("department").toString();
            String department = resultSet2.getString("department");

            EditMessageText message = new EditMessageText();
            message.setChatId(String.valueOf(chatId));
            message.setText(EmojiParser.parseToUnicode("Вы на " + course + " курсе" + "\nНа кафедре: " + department));
            message.setMessageId(messageId);
            try {
                execute(message);
            }catch (TelegramApiException e){
                log.error("Error occurred: " + e.getMessage());
            }
        } catch (SQLException e) {
            log.error("Error: " + e.getMessage());
        }
    }
    private void choiceDepartamentForCourse(long chatId, int messageId, int course) throws IOException {
        myCourse = course;
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(EmojiParser.parseToUnicode("на какой вы кафедре? " + "\uD83D\uDC80"));
        message.setMessageId(messageId);

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();

        FileInputStream file = new FileInputStream(new File(filePath));
        Workbook workbook = new HSSFWorkbook(file);
        var key = new InlineKeyboardButton();
        int k = 0;
        String result;
        arr.add(String.valueOf(course));
        for (int i = 2; i < 100; i++){
            result = workbook.getSheetAt(course-1).getRow(0).getCell(i).getStringCellValue();
            key.setText(result);
            key.setCallbackData(result);
            arr.add(key.getCallbackData());
            rowInline.add(key);
            k++;
            if (k == 2) {
                rowsInline.add(rowInline);
                rowInline = new ArrayList<>();
                k = 0;
            }
            key = new InlineKeyboardButton();
            if ((result.equals("ИУК7-31Б") && course == 2) || (result.equals("ИУК7-51Б")  && course == 3) || (result.equals("ИУК7-71Б")  && course == 4) || (result.equals("ИУК6-91")  && course == 5) || (result.equals("ИУК6-113")  && course == 6) || (result.equals("ИУК11-11М") && course == 7) || (result.equals("ИУК7-31М")  && course == 8)){
                break;
            }
        }
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        }catch (TelegramApiException e){
            log.error("Error occurred: " + e.getMessage());
        }

    }

    private void choiceDepartamentForThirdCourse(long chatId, int messageId){

    }
    private void choiceCourse(long chatId, int messageId){
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(EmojiParser.parseToUnicode("на каком вы курсе? " + "\uD83D\uDC80"));
        message.setMessageId(messageId);

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        List<InlineKeyboardButton> row2Inline = new ArrayList<>();
        List<InlineKeyboardButton> row3Inline = new ArrayList<>();
        List<InlineKeyboardButton> row4Inline = new ArrayList<>();

        var first = new InlineKeyboardButton();

        first.setText("1");
        first.setCallbackData("FIRST");

        var second = new InlineKeyboardButton();

        second.setText("2");
        second.setCallbackData("SECOND");

        var third = new InlineKeyboardButton();

        third.setText("3");
        third.setCallbackData("THIRD");

        var fourth = new InlineKeyboardButton();

        fourth.setText("4");
        fourth.setCallbackData("FOURTH");

        var fifth = new InlineKeyboardButton();

        fifth.setText("5");
        fifth.setCallbackData("FIFTH");

        var sixth = new InlineKeyboardButton();

        sixth.setText("6");
        sixth.setCallbackData("SIXTH");

        var magaFirst = new InlineKeyboardButton();

        magaFirst.setText("МГ 1 курс");
        magaFirst.setCallbackData("MGFIRST");

        var magaSecond = new InlineKeyboardButton();

        magaSecond.setText("МГ 2 курс");
        magaSecond.setCallbackData("MGSECOND");

        rowInline.add(first);
        rowInline.add(second);

        row2Inline.add(third);
        row2Inline.add(fourth);

        row3Inline.add(fifth);
        row3Inline.add(sixth);

        row4Inline.add(magaFirst);
        row4Inline.add(magaSecond);

        rowsInline.add(rowInline);
        rowsInline.add(row2Inline);
        rowsInline.add(row3Inline);
        rowsInline.add(row4Inline);

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        }catch (TelegramApiException e){
            log.error("Error occurred: " + e.getMessage());
        }

    }


    private void registerNew(long chatId, int messageId){
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(EmojiParser.parseToUnicode("вы хотите зарегистрироваться "));
        message.setMessageId(messageId);

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        List<InlineKeyboardButton> secondRowInline = new ArrayList<>();
        var yesButton = new InlineKeyboardButton();

        yesButton.setText("как студент");
        yesButton.setCallbackData("STUDENT_BUTTON");

        var noButton = new InlineKeyboardButton();

        noButton.setText("как преподаватель");
        noButton.setCallbackData("TEACHER_BUTTON");

        rowInline.add(yesButton);
        secondRowInline.add(noButton);

        rowsInline.add(rowInline);
        rowsInline.add(secondRowInline);

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        }catch (TelegramApiException e){
            log.error("Error occurred: " + e.getMessage());
        }

    }


    private void register(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(EmojiParser.parseToUnicode("вы хотите зарегистрироваться "));

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        List<InlineKeyboardButton> secondRowInline = new ArrayList<>();
        var yesButton = new InlineKeyboardButton();

        yesButton.setText("как студент");
        yesButton.setCallbackData("STUDENT_BUTTON");

        var noButton = new InlineKeyboardButton();

        noButton.setText("как преподаватель");
        noButton.setCallbackData("TEACHER_BUTTON");

        rowInline.add(yesButton);
        secondRowInline.add(noButton);

        rowsInline.add(rowInline);
        rowsInline.add(secondRowInline);

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        }catch (TelegramApiException e){
            log.error("Error occurred: " + e.getMessage());
        }

    }

    private void registerUser(Long chatId, Chat chat, String course, String department) {

            User user = new User();
            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            user.setCourse(course);
            user.setDepartment(department);

            userRepository.save(user);
            log.info("user saved: " + user);
    }

    private void startCommandReceived(long chatId, String name){

        String answer = EmojiParser.parseToUnicode("привет, " + name + ", рад тебя видеть "+ "❤\uFE0F\u200D\uD83D\uDD25");

        log.info("Replied to user " + name);

        sendMessage(chatId, answer);
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        try {
            execute(message);
        }catch (TelegramApiException e){
            log.error("Error occurred: " + e.getMessage());
        }

    }
}
