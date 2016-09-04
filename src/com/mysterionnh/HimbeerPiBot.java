package com.mysterionnh;

import twitter4j.Twitter;
import twitter4j.DirectMessage;
import twitter4j.ResponseList;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;

import java.io.*;

import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HimbeerPiBot {

    // TODO: Add lookup for twitterCommands.sh, may even make it relative
    // TODO: Not fail safe. Incorrect commands may lead to crashes (mail, exec etc.). Also no fail-safe against xss
    // TODO: Mails still end in "-e" and show the quotes they were made with
    // TODO: exec not working

    private static final String version = "0.1.9 - closed beta";

    private static final int ADMIN = 4, LOWBOB = 2, UNKNOWN = -1;
    private static final String twitterCommands = "/home/pi/bash/twitterCommands.sh";

    private static Map<String, Integer> commands = new HashMap<>();
    private static Map<String, Integer> verifiedUsers = new HashMap<>();
    private static Map<String, String> help = new HashMap<>();

    private static Twitter twitter;

    public static void main(String... args) throws TwitterException {

        iniBot();

        doScheduledTasks();

        int loopCount = 1;

        while (true) {
            log("dmLookupStart.." + loopCount);

            ResponseList<DirectMessage> directMessages = twitter.getDirectMessages();

            for (DirectMessage dm : directMessages) {
                String dmText =    dm.getText();
                String dmSender =  dm.getSenderScreenName();
                String dmCommand = dmText.contains(" ") ? dmText.substring(0, dm.getText().indexOf(' ')) : dmText;

                Integer userLevel = getUserLevel(dmSender);

                log(String.format("Found DirectMessage by %s", dmSender));

                log(String.format("Assigned userLevel = %d", userLevel));

                log(String.format("Full message is \"%s\", resulting in command \"%s\"", dmText, dmCommand));

                if (!commands.containsKey(dmCommand)) {
                    log("Unknown command, messaging user and aborting");
                    twitter.sendDirectMessage(dmSender, "Unknown command");
                    twitter.destroyDirectMessage(dm.getId());
                    continue;
                }

                if (!(userLevel >= commands.get(dmCommand))) {
                    log("User level to low to use command, messaging user and aborting");
                    twitter.sendDirectMessage(dmSender, "You are not allowed to use this command");
                    twitter.destroyDirectMessage(dm.getId());
                    continue;
                }

                String output = "The action produced the following output: ";

                switch (dmCommand) {
                    case "help":
                    {
                        String helpText = "You can use the following commands:" + System.getProperty("line.separator");
                        for (Map.Entry e : commands.entrySet()) {
                            if (userLevel >= (int) e.getValue()) {
                                helpText += help.get(e.getKey());
                                helpText += System.getProperty("line.separator");
                            }
                        }
                        log(helpText);
                        twitter.sendDirectMessage(dmSender, helpText);
                        break;
                    }
                    case "ping":
                    {
                        log("Pong!");
                        twitter.sendDirectMessage(dmSender, "Pong!");
                        break;
                    }
                    case "version":
                    {
                        log(version);
                        twitter.sendDirectMessage(dmSender, "I'm on version " + version);
                        break;
                    }
                    case "mail":
                    {
                        output += executeBash(dmText, dmSender);
                        log(output);
                        twitter.sendDirectMessage(dmSender, output);
                        break;
                    }
                    case "exec":
                    {
                        output += executeBash(dmText, null);
                        log(output);
                        twitter.sendDirectMessage(dmSender, output);
                        break;
                    }
                    case "reboot":
                    {
                        log("Rebooting");
                        twitter.sendDirectMessage(dmSender, "Rebooting");

                        scheduleDirectMessageForNextReboot(dmSender, "Reboot successful"); // message user after reboot

                        twitter.destroyDirectMessage(dm.getId()); // remove dm

                        executeBash("reboot", null);
                        return;
                    }
                    case "mailFile":
                    {
                        output += executeBash(dmText, dmSender);
                        log(output);
                        twitter.sendDirectMessage(dmSender, output);
                        break;
                    }
                    case "mailBackup":
                    {
                        output += executeBash(dmText, dmSender);
                        log(output);
                        twitter.sendDirectMessage(dmSender, output);
                        break;
                    }
                    default:
                    {
                        log("Unknown command in switch, which is.. weird. Needs attention my supervision, mailing me.");
                        twitter.sendDirectMessage(dm.getSender().getScreenName(), "Unknown Command");
                        executeBash("mail niklashalle1502@gmail.com 'Error in TwitterBot' 'Unknown command made it into switch'", "HimbeerPiBot");
                        break;
                    }
                }
                twitter.destroyDirectMessage(dm.getId()); // make sure we don't redo the same dm again and again
            }
            log("dmLookupEnd...." + loopCount++);
            sleep(60);
        }
    }

    private static void iniBot() throws TwitterException {
        // create/find needed files
        try {
            Files.createFile(Paths.get("scheduledTasks"));
            Files.createFile(Paths.get("twitterLog"));
        } catch (FileAlreadyExistsException e) {
            // That's nice, we already ran here
        } catch (IOException io) {
            // probably no file creation rights
            io.printStackTrace();
            System.exit(-1);
        }

        // key = command, value = minUserLvl
        commands.put("help", UNKNOWN);      // java
        commands.put("ping", UNKNOWN);      // java
        commands.put("version", UNKNOWN);   // java

        commands.put("mail", LOWBOB);       // bash

        commands.put("exec", ADMIN);        // bash
        commands.put("reboot", ADMIN);      // bash
        commands.put("mailFile", ADMIN);    // bash
        commands.put("mailBackup", ADMIN);  // bash

        // key = command, value = help text
        help.put("help", "help - displays this message. Usage: \"help\"");
        help.put("ping", "ping - Pong! Simple command to test if bot is running. Usage: \"ping\"");
        help.put("version", "version - displays the version of this bot. Usage: \"version\"");

        help.put("mail", "mail - Sends an E-Mail to a person. Usage: \"mail '{mailAddress}' '{mailSubject}' '{mailBody}'\"");

        help.put("exec", "exec - Executes a bash command. Usage: \"exec {bash command}\"");
        help.put("reboot", "reboot - Reboots the Pi. Usage: \"reboot\"");
        help.put("mailFile", "mailFile - Mails a file (as attachment) to a person. Usage: \"mailFile '{pathToFile}' '{mailSubject}' '{mailAddress}'\"");
        help.put("mailBackup", "mailBackup - Mail a backup of a directory to a person. Usage: \"mailBackup '{pathToDirectory}' '{backupName/mailSubject}' '{mailAddress}'\"");

        verifiedUsers.put("MysterionNH", ADMIN);
        verifiedUsers.put("NicosHypothetischerTwitterAccount", LOWBOB);

        twitter = TwitterFactory.getSingleton();

        log(System.getProperty("line.separator") + "--- New Session ---" + System.getProperty("line.separator"));

        // Message me about new start, regardless of cause
        twitter.sendDirectMessage("MysterionNH", "I just went online (likely the Pi rebooted, or, worst case, I was " +
                "started manually. If that's case, I'm going to reply twice. To fix, reboot.)");
    }

    private static void doScheduledTasks() throws TwitterException {
        String line;
        try (
                InputStream fis = new FileInputStream("scheduledTasks");
                InputStreamReader isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
                BufferedReader br = new BufferedReader(isr)
        ) {
            while ((line = br.readLine()) != null) {
                String name = line.substring(0, line.indexOf('-'));
                String msg = line.substring(line.indexOf('-') + 1);

                twitter.sendDirectMessage(name, msg);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new File("scheduledTasks"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        assert writer != null;
        writer.print("");
        writer.close();
    }

    private static void scheduleDirectMessageForNextReboot(String userName, String msg) {
        try {
            Files.write(Paths.get("scheduledTasks"), (userName + "-" + msg).getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            // probably no file write rights
            e.printStackTrace();
        }
    }

    private static Integer getUserLevel(String userName) {
        Integer userLevel = verifiedUsers.get(userName);
        if (userLevel == null) userLevel = UNKNOWN;
        return userLevel;
    }

    private static String executeBash(String command, String sender) {
        List<String> matchList = new ArrayList<>();
        matchList.add(twitterCommands);

        Pattern regex = Pattern.compile("[^\\s\"']+|\"[^\"]*\"|'[^']*'");
        Matcher regexMatcher = regex.matcher(command);

        int j = 0;
        while (regexMatcher.find()) {
            if (j == 1 && (sender != null && !sender.isEmpty())) {
                matchList.add(sender);
                j++;
            } else {
                j++;
            }
            matchList.add(regexMatcher.group());
        }

        String[] temp = new String[matchList.size()];

        for (int i = 0; i < matchList.size(); i++) {
            temp[i] = matchList.get(i);
            log(temp[i]);
        }

        Process bash = null;
        try {
            bash = new ProcessBuilder(temp).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return getProcessOutput(bash);
    }

    private static String getProcessOutput(Process pc) {
        if (pc == null) return "";

        BufferedReader reader =
                new BufferedReader(new InputStreamReader(pc.getInputStream()));
        StringBuilder builder = new StringBuilder();
        String line;
        try {
            while ( (line = reader.readLine()) != null) {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

    private static void log(String text) {
        text += System.getProperty("line.separator");
        try {
            Files.write(Paths.get("twitterLog"), text.getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(text);
    }

    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}