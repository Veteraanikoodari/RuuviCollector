package fi.tkgwf.ruuvi.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import fi.tkgwf.ruuvi.handler.BeaconHandler;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.apache.log4j.Logger;


public abstract class Utils {

    private static final Logger LOG = Logger.getLogger(BeaconHandler.class);

    /**
     * Converts a space-separated string of hex to ASCII
     *
     * @param hex space separated string of hex
     * @return the ASCII representation of the hex string
     */
    public static String hexToAscii(String hex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 3) {
            sb.append((char) Integer.parseInt(hex.substring(i, i + 2), 16));
        }
        return sb.toString();
    }

    /**
     * Converts a hex sequence to raw bytes
     *
     * @param hex the hex string to parse
     * @return a byte-array containing the byte-values of the hex string
     */
    public static byte[] hexToBytes(String hex) {
        String s = hex.replaceAll(" ", "");
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len - 1 /*-1 because we'll read two at a time*/; i += 2) {
            data[i / 2] =
                    (byte)
                            ((Character.digit(s.charAt(i), 16) << 4)
                                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Use to read line from hcidump and confirm if Mac address should be present
     *
     * @param line a space separated string of hex, first six decimals are assumed to be part of the
     *     MAC address, rest of the line is discarded
     * @return true if Mac address should be found, false if Mac address should not be present
     */
    public static boolean hasMacAddress(String line) {
        return line != null && line.startsWith("> ") && line.trim().length() > 37; //
    }

    /**
     * Gets a MAC address from a space-separated hex string
     *
     * @param line a space separated string of hex, this string is checked by {@link
     *     #hasMacAddress(String)}
     * @return the MAC address, without spaces
     */
    public static String getMacFromLine(String line) {
        if (!hasMacAddress(line)) {
            return null;
        }

        String[] terms = line.split(" ");
        if (terms.length <= 13) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 13; i >= 8; i--) {
            sb.append(terms[i]);
        }
        return sb.toString();
    }

    /**
     * Convenience method for checking whether the supplied byte is the max signed byte. (Java
     * doesn't natively have unsigned primitives)
     *
     * @param b byte to check
     * @return true if the byte represents the max value a signed byte can be
     */
    public static boolean isMaxSignedByte(byte b) {
        return (b & 0xFF) == 127;
    }

    /**
     * Convenience method for checking whether the supplied byte is the max unsigned byte. (Java
     * doesn't natively have unsigned primitives)
     *
     * @param b byte to check
     * @return true if the byte represents the max value an unsigned byte can be
     */
    public static boolean isMaxUnsignedByte(byte b) {
        return (b & 0xFF) == 255;
    }

    /**
     * Convenience method for checking whether the supplied bytes forming a 16bit short is the max
     * signed short. (Java doesn't natively have unsigned primitives)
     *
     * @param b1 1st byte to check
     * @param b2 2nd byte to check
     * @return true if the pair of bytes represent the max value a signed short can be
     */
    public static boolean isMaxSignedShort(byte b1, byte b2) {
        return isMaxSignedByte(b1) && isMaxUnsignedByte(b2);
    }

    /**
     * Convenience method for checking whether the supplied bytes forming a 16bit short is the max
     * unsigned short. (Java doesn't natively have unsigned primitives)
     *
     * @param b1 1st byte to check
     * @param b2 2nd byte to check
     * @return true if the pair of bytes represent the max value an unsigned short can be
     */
    public static boolean isMaxUnsignedShort(byte b1, byte b2) {
        return isMaxUnsignedByte(b1) && isMaxUnsignedByte(b2);
    }

    /**
     * Reads configuration for class from yml file which has the same name (in lowercase) as class
     * simple name. First from working dir and then from resources
     */
    public static <T> T readYamlConfig(Class<T> clazz) throws IOException {
        var nameWithPath =
                System.getProperty("user.dir") + "/" + clazz.getSimpleName().toLowerCase() + ".yml";
        var file = new File(nameWithPath);
        LOG.info("Looking configuration from: " + file.getAbsolutePath());
        LOG.info(file.isFile() ? "..found." : "..not found. Using defaults from resources.");
        try (var inputStream =
                file.isFile()
                        ? new FileInputStream(file)
                        : getInputSreamFromResources(clazz.getSimpleName())) {
            Yaml yaml = new Yaml(new Constructor(clazz));
            return yaml.load(inputStream);
        }
    }

    private static InputStream getInputSreamFromResources(String name) {
        return com.sun.tools.javac.Main.class
                .getClassLoader()
                .getResourceAsStream(name.toLowerCase() + ".yml");
    }
}
