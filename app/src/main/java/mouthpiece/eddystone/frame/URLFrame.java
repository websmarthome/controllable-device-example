/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mouthpiece.eddystone.frame;

import android.bluetooth.le.AdvertiseData;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseArray;
import android.webkit.URLUtil;

import java.net.URISyntaxException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * Most of this code is borrowed from original URLFrame repository
 */

public class URLFrame {

    private static final String TAG = URLFrame.class.getSimpleName();

  /**
   * The Service Data UUID is the reserved 16-bit Service Data Code in the form of a globally unique
   * 128-bit UUID.
   */
    public static final ParcelUuid URI_SERVICE_UUID =
        ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");
      //ParcelUuid.fromString("0000FED8-0000-1000-8000-00805F9B34FB");
    public static final ParcelUuid DEPRECATED_URI_SERVICE_UUID =
        ParcelUuid.fromString("0000FED8-0000-1000-8000-00805F9B34FB");
    public static final byte NO_TX_POWER_LEVEL = -101;
    public static final byte NO_FLAGS = 0;
    public static final String NO_URI = "";

    private static final int DATA_TYPE_SERVICE_DATA = 0x16;

    private static final byte[] URI_SERVICE_16_BIT_UUID_BYTES = {(byte) 0xaa, (byte) 0xfe};
    private static final byte[] DEPRECATED_URI_SERVICE_16_BIT_UUID_BYTES = {(byte) 0xd8, (byte) 0xfe};

    public static AdvertiseData createAdvertiseData(String url) {
        AdvertiseData.Builder builder = new AdvertiseData.Builder();
        builder.setIncludeTxPowerLevel(false); // reserve advertising space for URI

        byte[] uriData = URLFrame.encodeUri(url);
        byte[] beaconData = new byte[2 + uriData.length];
        beaconData[0] = (byte)0x10; // flags
        beaconData[1] = (byte) 0xBA; // transmit power
        for (int i = 0; i < uriData.length; i++) {
            beaconData[i+2] = uriData[i];
        }

        builder.addServiceData(URLFrame.URI_SERVICE_UUID, beaconData);
        // Adding 0xFED8 to the "Service Complete List UUID 16" (0x3) for iOS compatibility
        builder.addServiceUuid(URLFrame.URI_SERVICE_UUID);

        return builder.build();
    }
  /**
   * URI Scheme maps a byte code into the scheme and an optional scheme specific prefix.
   */
  private static final SparseArray<String> URI_SCHEMES = new SparseArray<String>() {{
    put((byte) 0, "http://www.");
    put((byte) 1, "https://www.");
    put((byte) 2, "http://");
    put((byte) 3, "https://");
    put((byte) 4, "urn:uuid:");    // RFC 2141 and RFC 4122};
  }};
  /**
   * Expansion strings for "http" and "https" schemes. These contain strings appearing anywhere in a
   * URL. Restricted to Generic TLDs. <p/> Note: this is a scheme specific encoding.
   */
  private static final SparseArray<String> URL_CODES = new SparseArray<String>() {{
    put((byte) 0, ".com/");
    put((byte) 1, ".org/");
    put((byte) 2, ".edu/");
    put((byte) 3, ".net/");
    put((byte) 4, ".info/");
    put((byte) 5, ".biz/");
    put((byte) 6, ".gov/");
    put((byte) 7, ".com");
    put((byte) 8, ".org");
    put((byte) 9, ".edu");
    put((byte) 10, ".net");
    put((byte) 11, ".info");
    put((byte) 12, ".biz");
    put((byte) 13, ".gov");
  }};
  private static final int FLAGS_FIELD_SIZE = 3;
  private static final int URI_SERVICE_FLAGS_TXPOWER_SIZE = 2;
  private static final byte[] URI_SERVICE_UUID_FIELD = {(byte) 0x03, (byte) 0x03, (byte) 0xD8,
      (byte) 0xFE};
  private static final byte[] URI_SERVICE_DATA_FIELD_HEADER = {0x16, (byte) 0xD8, (byte) 0xFE};
  private static final int MAX_ADVERTISING_DATA_BYTES = 31;
  private static final int MAX_URI_LENGTH = 18;
  private final byte mFlags;
  private final byte mTxPowerLevel;
  private final String mUriString;

  // Copy constructor
  URLFrame(URLFrame frame) {
    mUriString = frame.getUriString();
    mFlags = frame.getFlags();
    mTxPowerLevel = frame.getTxPowerLevel();
  }

  /**
   * Creates the Uri string with embedded expansion codes.
   *
   * @param uri to be encoded
   * @return the Uri string with expansion codes.
   */
  public static byte[] encodeUri(String uri) {
    if (uri.length() == 0) {
      return new byte[0];
    }
    ByteBuffer bb = ByteBuffer.allocate(uri.length());
    // UUIDs are ordered as byte array, which means most significant first
    bb.order(ByteOrder.BIG_ENDIAN);
    int position = 0;

    // Add the byte code for the scheme or return null if none
    Byte schemeCode = encodeUriScheme(uri);
    if (schemeCode == null) {
      return null;
    }
    String scheme = URI_SCHEMES.get(schemeCode);
    bb.put(schemeCode);
    position += scheme.length();

    if (URLUtil.isNetworkUrl(scheme)) {
      return encodeUrl(uri, position, bb);
    } else if ("urn:uuid:".equals(scheme)) {
      return encodeUrnUuid(uri, position, bb);
    }
    return null;
  }

  /**
   * @return The Uri that will be broadcasted in a byte[]
   */
  public byte[] getUriBytes() {
    return encodeUri(mUriString);
  }

  /**
   * @return the Uri flags indicating the discoverable mode and capability of the device.
   */
  public byte getFlags() {
    return mFlags;
  }

  /**
   * @return the transmission power level of the packet in dBm. This value can be used to calculate
   * the path loss of a received packet using the following equation: <p/> <code>path loss =
   * txPowerLevel - RSSI</code>
   */
  public byte getTxPowerLevel() {
    return mTxPowerLevel;
  }

  /**
   * @return the Uri text of the packet.
   */
  public String getUriString() {
    return mUriString;
  }

  /**
   * Parse scan record bytes to {@link URLFrame}. <p/> The format is defined in Eddystone-URL
   * Definition.
   *
   * @param scanRecordBytes The scan record of Bluetooth LE advertisement and/or scan response.
   */
  public static URLFrame parseFromBytes(byte[] scanRecordBytes) {
      ServiceDataParseResult result = parseServiceDataFromBytes(scanRecordBytes);
      //byte[] serviceData = parseServiceDataFromBytes(scanRecordBytes);
      // Minimum URLFrame consists of flags, TxPower
      if (result == null || result.bytes == null || result.bytes.length < 3) {
          return null;
      }
      byte[] serviceData = result.bytes;
      int currentPos = 0;
      byte flags = serviceData[currentPos++];
      if (result.isEddystone && (flags != (byte)0x10)) {
          // flags(frameType) 0x00:UID 0x20:TLM 0x10:URL
          Log.i(TAG, "found Eddystone, but it's not Eddystone-URL, but a UID or TLM");
          return null;
      }
      byte txPowerLevel = serviceData[currentPos++];
      String uri = decodeUri(serviceData, currentPos);
      return new URLFrame(flags, txPowerLevel, uri);
  }

  @Override
  public String toString() {
    return String.format(Locale.ENGLISH,
        "%s@(uri:'%s' txPowerLevel:%d flags:%d)",
        getClass().getSimpleName(), mUriString, mTxPowerLevel, mFlags);
  }

  /**
   * The advertisement data for the URLFrame as a byte array.
   *
   * @return the URLFrame bytes
   */
  public byte[] toByteArray() {
    int totalUriBytes = totalBytes(mUriString);
    if (totalUriBytes == 0) {
      return null;
    }
    ByteBuffer buffer = ByteBuffer.allocateDirect(totalUriBytes);
    buffer.put(URI_SERVICE_UUID_FIELD);
    byte[] uriBytes;
    uriBytes = encodeUri(mUriString);
    byte length = (byte) (URI_SERVICE_DATA_FIELD_HEADER.length +
        URI_SERVICE_FLAGS_TXPOWER_SIZE + uriBytes.length);
    buffer.put(length);
    buffer.put(URI_SERVICE_DATA_FIELD_HEADER);
    buffer.put(mFlags);
    buffer.put(mTxPowerLevel);
    buffer.put(uriBytes);
    return byteBufferToArray(buffer);
  }

  /**
   *
   * @param uriString
   * @return
   */
  public static int uriLength(String uriString) {
    byte[] encodedUri = encodeUri(uriString);
    if (encodedUri == null) {
      return -1;
    } else {
      return encodedUri.length;
    }
  }

  public static class Builder {

    private String mUriString;
    private byte[] mUriBytes;
    private byte mFlags = NO_FLAGS;
    private byte mTxPowerLevel = NO_TX_POWER_LEVEL;

    /**
     * Add a Uri to the UriBeacon advertised data.
     *
     * @param uriString The Uri to be advertised.
     * @return The UriBeacon Builder.
     */
    public Builder uriString(String uriString) {
      mUriString = uriString;
      return this;
    }

    public Builder uriString(byte[] uriBytes) {
      mUriBytes = uriBytes;
      return this;
    }

    /**
     * Add flags to the UriBeacon advertised data.
     *
     * @param flags The flags to be advertised.
     * @return The UriBeacon Builder.
     */
    public Builder flags(byte flags) {
      mFlags = flags;
      return this;
    }

    /**
     * Add a Tx Power Level to the UriBeacon advertised data.
     *
     * @param txPowerLevel The TX Power Level to be advertised.
     * @return The UriBeacon Builder.
     */
    public Builder txPowerLevel(byte txPowerLevel) {
      mTxPowerLevel = txPowerLevel;
      return this;
    }

    /**
     * Build the beacon.
     *
     * @return The UriBeacon
     * @throws URISyntaxException if the uri provided is not valid
     */
    public URLFrame build() throws URISyntaxException {
      if (mUriBytes != null) {
        mUriString = decodeUri(mUriBytes, 0);
        if (mUriString == null) {
          throw new IllegalArgumentException("Could not decode URI");
        }
      }

      if (mUriString == null) {
        throw new IllegalArgumentException(
            "URLFrame advertisements must include a URI");
      }
      // The firmware adds ADV Flags taking up 3 bytes so we can only send 31 - 3 = 28 bytes.
      // The Service UUID is 4 bytes. The Service Data contains a header (4 bytes),
      // Flags (1 byte) and  TX Power Level (1 byte).
      // So this works out to 28 - 4 - 4 - 1 - 1 = 18 bytes for Service Data Uri.
      int length = uriLength(mUriString);
      if (length > MAX_URI_LENGTH) {
        throw new URISyntaxException(mUriString, "Uri size is larger than "
            + MAX_URI_LENGTH + " bytes");
      } else if (length == -1) {
        throw new URISyntaxException(mUriString, "Not a valid URI");
      }
      return new URLFrame(mFlags, mTxPowerLevel, mUriString);
    }
  }

  private URLFrame(byte flags, byte txPowerLevel, String uriString) {
    mFlags = flags;
    mTxPowerLevel = txPowerLevel;
    mUriString = uriString;
  }

  private static String decodeUri(byte[] serviceData, int offset) {
    if (serviceData.length == 0) {
      return NO_URI;
    }
    StringBuilder uriBuilder = new StringBuilder();
    if (offset < serviceData.length) {
      byte b = serviceData[offset++];
      String scheme = URI_SCHEMES.get(b);
      if (scheme != null) {
        uriBuilder.append(scheme);
        if (URLUtil.isNetworkUrl(scheme)) {
          return decodeUrl(serviceData, offset, uriBuilder);
        } else if ("urn:uuid:".equals(scheme)) {
          return decodeUrnUuid(serviceData, offset, uriBuilder);
        }
      }
      Log.w(TAG, "decodeUri unknown Uri scheme code=" + b);
    }
    return null;
  }

  private static String decodeUrl(byte[] serviceData, int offset, StringBuilder urlBuilder) {
    while (offset < serviceData.length) {
      byte b = serviceData[offset++];
      String code = URL_CODES.get(b);
      if (code != null) {
        urlBuilder.append(code);
      } else {
        urlBuilder.append((char) b);
      }
    }
    return urlBuilder.toString();
  }

  private static String decodeUrnUuid(byte[] serviceData, int offset, StringBuilder urnBuilder) {
    ByteBuffer bb = ByteBuffer.wrap(serviceData);
    // UUIDs are ordered as byte array, which means most significant first
    bb.order(ByteOrder.BIG_ENDIAN);
    long mostSignificantBytes, leastSignificantBytes;
    try {
      bb.position(offset);
      mostSignificantBytes = bb.getLong();
      leastSignificantBytes = bb.getLong();
    } catch (BufferUnderflowException e) {
      Log.w(TAG, "decodeUrnUuid BufferUnderflowException!");
      return null;
    }
    UUID uuid = new UUID(mostSignificantBytes, leastSignificantBytes);
    urnBuilder.append(uuid.toString());
    return urnBuilder.toString();
  }

  /**
   * Finds the longest expansion from the uri at the current position.
   *
   * @param uriString the Uri
   * @param pos start position
   * @return an index in URI_MAP or 0 if none.
   */
  private static byte findLongestExpansion(String uriString, int pos) {
    byte expansion = -1;
    int expansionLength = 0;
    for (int i = 0; i < URL_CODES.size(); i++) {
      // get the key and value.
      int key = URL_CODES.keyAt(i);
      String value = URL_CODES.valueAt(i);
      if (value.length() > expansionLength && uriString.startsWith(value, pos)) {
        expansion = (byte) key;
        expansionLength = value.length();
      }
    }
    return expansion;
  }

  private static Byte encodeUriScheme(String uri) {
    String lowerCaseUri = uri.toLowerCase(Locale.ENGLISH);
    for (int i = 0; i < URI_SCHEMES.size(); i++) {
      // get the key and value.
      int key = URI_SCHEMES.keyAt(i);
      String value = URI_SCHEMES.valueAt(i);
      if (lowerCaseUri.startsWith(value)) {
        return (byte) key;
      }
    }
    return null;
  }

  private static byte[] encodeUrl(String url, int position, ByteBuffer bb) {
    while (position < url.length()) {
      byte expansion = findLongestExpansion(url, position);
      if (expansion >= 0) {
        bb.put(expansion);
        position += URL_CODES.get(expansion).length();
      } else {
        bb.put((byte) url.charAt(position++));
      }
    }
    return byteBufferToArray(bb);
  }

  private static byte[] encodeUrnUuid(String urn, int position, ByteBuffer bb) {
    String uuidString = urn.substring(position, urn.length());
    UUID uuid;
    try {
      uuid = UUID.fromString(uuidString);
    } catch (IllegalArgumentException e) {
      Log.w(TAG, "encodeUrnUuid invalid urn:uuid format - " + urn);
      return null;
    }
    // UUIDs are ordered as byte array, which means most significant first
    bb.order(ByteOrder.BIG_ENDIAN);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return byteBufferToArray(bb);
  }

  private static byte[] byteBufferToArray(ByteBuffer bb) {
    byte[] bytes = new byte[bb.position()];
    bb.rewind();
    bb.get(bytes, 0, bytes.length);
    return bytes;
  }

  private static byte[] UuidToByteArray(UUID uuid) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return byteBufferToArray(bb);
  }

  // Compute the size of the advertisement data in the Service UUID and Service Data fields.
  // This does not include the ADV Flag Fields (3 bytes).
  private static int totalBytes(String uriString) {
    byte[] encodedUri = encodeUri(uriString);
    if (encodedUri == null) {
      return 0;
    }
    int size = URI_SERVICE_UUID_FIELD.length;
    size += 1; // length is one byte
    size += URI_SERVICE_DATA_FIELD_HEADER.length;
    size += 1; // flags is one byte.
    size += 1; // tx power level value is one byte.
    size += encodedUri.length;
    return size;
  }



    private static class ServiceDataParseResult {
        public  byte[] bytes;
        public boolean isEddystone = false;
        ServiceDataParseResult() {
        }

    }

    private static ServiceDataParseResult parseServiceDataFromBytes(byte[] scanRecord) {
        int currentPos = 0;
        try {
            while (currentPos < scanRecord.length) {
                int fieldLength = scanRecord[currentPos++] & 0xff;
                if (fieldLength == 0) {
                    break;
                }
                int fieldType = scanRecord[currentPos++] & 0xff;
                if (fieldType == DATA_TYPE_SERVICE_DATA) {
                    // The first two bytes of the service data are service data UUID.

                    byte uuid16first  = scanRecord[currentPos++];
                    byte uuid16second = scanRecord[currentPos++];

                    boolean isEddystone = (uuid16first == URI_SERVICE_16_BIT_UUID_BYTES[0]
                            && uuid16second == URI_SERVICE_16_BIT_UUID_BYTES[1]);
                    boolean isUrlBeacon = (uuid16first == DEPRECATED_URI_SERVICE_16_BIT_UUID_BYTES[0]
                            && uuid16second == DEPRECATED_URI_SERVICE_16_BIT_UUID_BYTES[1]);
                    if (isEddystone || isUrlBeacon) {


                        byte[] bytes = new byte[fieldLength - 3];
                        System.arraycopy(scanRecord, currentPos, bytes, 0, fieldLength - 3);
                        ServiceDataParseResult result = new ServiceDataParseResult();
                        result.isEddystone = isEddystone;
                        result.bytes = bytes;
                        return result;
                    }
                }
                // length includes the length of the field type
                currentPos += fieldLength - 1;
            }
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "unable to parse scan record: " + Arrays.toString(scanRecord));
        }
        return null;
    }
}
