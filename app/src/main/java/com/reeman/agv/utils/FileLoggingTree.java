package com.reeman.agv.utils;

import android.text.TextUtils;

import com.elvishew.xlog.XLog;
import com.elvishew.xlog.flattener.ClassicFlattener;
import com.elvishew.xlog.printer.AndroidPrinter;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.backup.BackupStrategy2;
import com.elvishew.xlog.printer.file.backup.FileSizeBackupStrategy2;
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy;
import com.reeman.log.CustomFlattener;
import com.reeman.log.LogFileName;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

public class FileLoggingTree extends Timber.Tree {

    private final String defaultTAG;

    private final int logLevel;

    private final Map<String, Printer> printerMap = new HashMap<>();

    private final Printer androidPrinter = new AndroidPrinter(true);

    private Set<String> blockedTags;

    private Set<String> blackedMessages;

    private boolean withAndroidPrinter = false;


    public FileLoggingTree(int priority, String rootPath, String tag, boolean withAndroidPrinter, List<String> pathList) {
        this.logLevel = priority;
        this.defaultTAG = tag;
        this.withAndroidPrinter = withAndroidPrinter;
        for (String path : pathList) {
            FilePrinter.Builder builder = new FilePrinter
                    .Builder(rootPath + File.separator + path)
                    .fileNameGenerator(new LogFileName())
                    .backupStrategy(new FileSizeBackupStrategy2(1024 * 1024 * 5, BackupStrategy2.NO_LIMIT))
                    .cleanStrategy(new FileLastModifiedCleanStrategy(7 * 24 * 60 * 60 * 1000));
            if (path.equals("power_board_log")) {
                builder.flattener(new CustomFlattener("{m}"));
            } else {
                builder.flattener(new ClassicFlattener());
            }
            printerMap.put(path, builder.build());

        }
    }


    public FileLoggingTree(int priority, String tag, Map<String, Printer> printerMap) {
        this.logLevel = priority;
        this.defaultTAG = tag;
        this.printerMap.putAll(printerMap);

    }

    public void setBlockedTags(Set<String> blockedTags) {
        this.blockedTags = blockedTags;
    }

    public void setBlackedMessages(Set<String> blackedMessages) {
        this.blackedMessages = blackedMessages;
    }

    @Override
    protected void log(int priority, String tag, @NotNull String message, Throwable t) {
        if (tag == null) {
            tag = defaultTAG;
        }
        if (priority < logLevel) {
            return;
        }
        if (!shouldLog(tag, message)) return;
        Printer finalFilePrinter = printerMap.get(printerMap.containsKey(tag) ? tag : defaultTAG);
        Printer[] printers;
        if (withAndroidPrinter && !Arrays.asList(com.reeman.commons.BuildConfig.WHEEL_INFO_DIR,
                com.reeman.commons.BuildConfig.BATTERY_REPORT_DIR,
                com.reeman.serialport.BuildConfig.LOG_POWER_BOARD).contains(tag)) {
            printers = new Printer[]{androidPrinter, finalFilePrinter};
        } else {
            printers = new Printer[]{finalFilePrinter};
        }
        if (t == null) {
            XLog.tag(tag).printers(printers).log(priority, message);
        } else {
            XLog.tag(tag).printers(printers).log(priority, message, t);
        }
    }

    private boolean shouldLog(String tag, String message) {
        if (blockedTags != null && blockedTags.contains(tag)) return false;
        if (blackedMessages != null && !TextUtils.isEmpty(message)) {
            for (String blackedMessage : blackedMessages) {
                if (message.contains(blackedMessage)) return false;
            }
        }
        return true;
    }
}
