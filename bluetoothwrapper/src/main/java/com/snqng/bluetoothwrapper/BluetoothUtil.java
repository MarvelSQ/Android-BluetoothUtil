package com.snqng.bluetoothwrapper;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Created by sunqiang on 2017/3/22.
 */

public class BluetoothUtil {

    public static final String NEW = "0000FFE0-0000-1000-8000-00805F9B34FB";
    public static final String BASE = "00000000—0000—1000—8000—00805F9B34FB";
    public static final String DEFAULF = "00001101-0000-1000-8000-00805F9B34FB";

    public static final int DATA_TYPE_FLAGS = 0x01;
    public static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL = 0x02;
    public static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE = 0x03;
    public static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL = 0x04;
    public static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE = 0x05;
    public static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL = 0x06;
    public static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE = 0x07;
    public static final int DATA_TYPE_LOCAL_NAME_SHORT = 0x08;
    public static final int DATA_TYPE_LOCAL_NAME_COMPLETE = 0x09;
    public static final int DATA_TYPE_TX_POWER_LEVEL = 0x0A;
    public static final int DATA_TYPE_SERVICE_DATA = 0x16;
    public static final int DATA_TYPE_MANUFACTURER_SPECIFIC_DATA = 0xFF;

    public static List<UUID> getUuidsFromRecord(byte[] scanRecord) {
        ArrayList<UUID> uuids = new ArrayList<>();
        int curposition = 0;
        while (curposition < scanRecord.length) {
            int len = scanRecord[curposition] & 0xff;
            if (len != 0) {
                int type = scanRecord[curposition + 1] & 0xff;
                if (type == DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE || type == DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL) {
                    uuids.add(getUUidFromRecord(scanRecord, curposition + 2, len - 1));
                }
                curposition = curposition + len + 1;
            }else {
                curposition = scanRecord.length;
            }
        }
        return uuids;
    }

    private static UUID getUUidFromRecord(byte[] record, int start, int length) {
        String uuid = "";
        String cell;
        for (int i = length; i > 0; i--) {
            cell = Integer.toHexString(record[i + start - 1] & 0xff);
            uuid = cell.length() == 1 ? uuid + 0 + cell : uuid + cell;
            if (i % 2 == 1) {
                if (6 < i && i < 14) {
                    uuid = uuid + "-";
                }
            }
        }
        return UUID.fromString(uuid);
    }
}
