package com.nnapz;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Simple helper to count colors in a indexed gif image file with 256 or 16 index colors.
 * <p>
 * It iterates through the pixels of a file and prints the summary in a new html file generated aside the input file.
 * <p>
 * Not tuned; writte to remain understandable ;-)
 *
 * @author bs.software@gmx.de
 */
public class CountGifColors {

    private static final boolean DEBUG = false;
    private static DecimalFormat percentageFormat = new DecimalFormat("0.000");

    /**
     * @param args a file name or a directory name. If a directory is given and more than one file is found there, a
     *             index.html will be created.
     */
    public static void main(String[] args) {
        try {
            File[] files = null;
            if (args == null || args.length == 0) {
                final File myDir = new File(".");
                print("Look for Files in " + myDir.getAbsolutePath());
                files = getFilesFromDir(myDir);
            } else {
                File inFile = new File(args[0]);
                if (inFile.isDirectory()) {
                    print("Look for Files in " + inFile.getAbsolutePath());
                    files = getFilesFromDir(inFile);
                } else {
                    if (inFile.getName().toLowerCase().endsWith("gif")) {
                        files = new File[]{inFile};
                        print("process " + inFile.getAbsolutePath());
                    }
                }
            }
            if (files == null || files.length == 0) {
                print("Please provide a gif file name or a directory name (that contains gif files)");
                System.exit(1);
            }

            HashSet<File> generatedFiles = new HashSet<>();
            for (File file : files) {
                try {
                    final File newFile = processOneImage(file);
                    if (newFile != null) generatedFiles.add(newFile);
                } catch (Exception ex) {
                    print("    ERROR in processing: " + ex.getMessage());
                }
            }
            if (generatedFiles.size() == 0) {
                print("  ERROR: no files generated :-(");
                System.exit(1);
            }
            if (generatedFiles.size() == 1) {
                print(" Generated " + generatedFiles.iterator().next().getAbsolutePath());
            } else {
                // we have some files
                IndexHtmlFile index = new IndexHtmlFile();
                for (File file : generatedFiles) {
                    index.appendFile(file);
                }
                File indexFile = index.write();
                print(" Generated Index " + indexFile.getAbsolutePath() + " with " + generatedFiles.size() + " Links");

            }
        } catch (Throwable e) {
            print(" ERROR: " + e.getMessage());
            e.printStackTrace();

        }

    }

    private static File processOneImage(File file) throws IOException {

        final String fileName = file.getName();
        File outFile;  // set later if all goes well;
        print(" Process " + fileName);
        HashMap<Byte, Integer> colorCount = new HashMap<>();

        // try to unpack the image with java's base libraries
        BufferedImage image = ImageIO.read(file);
        ColorModel colorModel = image.getColorModel();
        debug("Colormodel: " + colorModel);
        IndexColorModel indexColorModel;
        if (colorModel instanceof IndexColorModel) {
            indexColorModel = (IndexColorModel) colorModel;
        } else {
            print("  ERROR: No IndexColorModel, skip file");
            return null;
        }

        final WritableRaster raster = image.getRaster();
        DataBuffer dataBuffer = raster.getDataBuffer();
        DataBufferByte dataBufferByte;
        if (dataBuffer instanceof DataBufferByte) {
            dataBufferByte = (DataBufferByte) dataBuffer;
        } else {
            print("  ERROR: No DataBufferByte");
            return null;
        }

        // count pixels
        int w = image.getWidth();
        int h = image.getHeight();
        byte data[] = dataBufferByte.getData();
        debug("data = " + data.length + ", w: " + w + " h: " + h);

        // 256 colors: 8 bit, otherwise the gif maybe compressed to have 2 pixels pr byte
        int bitsPerPixel = indexColorModel.getPixelSize();
        final int pixelPerByte = 8 / bitsPerPixel;
        int lineLength = w / pixelPerByte;
        debug("Bits per Pixel: " + bitsPerPixel + " bytes per image row: " + lineLength);
        int totalPixels = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < lineLength; x++) {
                totalPixels += pixelPerByte;
                int arrayIndex = x + y * lineLength;
                byte colorIndex = data[arrayIndex];  //  -128..127
                int left = 0;
                int right = 0;
                if (colorIndex != 0) {
                    if (pixelPerByte == 1) {
                        // a 256 colors gif
                        left = colorIndex;
                    } else if (pixelPerByte == 2) {
                        // a 16 colors image
                        String bitString = pad(Integer.toBinaryString(colorIndex), 8);      // excuse; I know bit shifting would be more elegant
                        String leftString = bitString.substring(0, 4);
                        String rightString = bitString.substring(4);

                        left = Byte.parseByte(leftString, 2);
                        right = Byte.parseByte(rightString, 2);

                    } else {
                        print("   ERROR: No support for one color gifs");
                        return null;
                    }
                }

                increaseCount(colorCount, (byte) left);
                increaseCount(colorCount, (byte) right);
            }
            //break;      for debugging, stop here after one row
        }

        // output
        final Set<Byte> colorCountSet = colorCount.keySet();
        final Byte[] colors = colorCountSet.toArray(new Byte[colorCount.size()]);
        Arrays.sort(colors);
        debug("   colors = " + colors.length);
        if (colors.length == 0) {
            print("ERROR: no colors counted ?!");
            return null;
        }
        OneImageFile html = new OneImageFile();
        html.append("<h2>").append(file.getName()).append("</h2>");
        html.append("<div>").append(colors.length).append(" Colors, ").append(w).append("x").append(h).append(" Pixels").append("</div>");
        html.append("<div class='cc_img'><img src='").append(file.getName()).append("'/></div>");
        html.append("<table class='cc_colortable'><tr><th>Color</th><th>Count</th><th>Percentage</th></tr>");
        debug("   totalPixels = " + totalPixels);
        int inxCount = 0;
        for (int key : colors) {
            int color = indexColorModel.getRGB(key);
            Color c = new Color(color);
            int inx = key < 0 ? -key + 254 : key;
            final Integer pixelCount = colorCount.get((byte) key);
            final String htmlColorCode = pad(Integer.toHexString(c.getRed()), 2) + pad(Integer.toHexString(c.getGreen()), 2) + pad(Integer.toHexString(c.getBlue()), 2);
            final double percentage = (double) pixelCount / (double) totalPixels * 100.0;
            debug((
                    "Color #" + inx + ": " + pixelCount + " Pixels, =" + percentage + "% " +
                            //      " #" + Integer.toHexString(color)) +
                            " HTML-Color: #" + htmlColorCode));
            html.append("<tr>")
                    .append("<td class='cc_color' bgcolor='").append(htmlColorCode).append("'>").append(inxCount).append(". #").append(htmlColorCode).append("</td>")
                    .append("<td>").append(pixelCount).append("<td>")
                    .append("<td>").append(percentageFormat.format(percentage)).append("</td>")
                    .append("</tr>");
            inxCount++;
        }
        html.append("</table>");
        outFile = html.write(file);

        return outFile;
    }

    private static void print(String s) {
        System.out.println(s);
    }

    private static void debug(String s) {
        if (DEBUG) System.out.println("     DEBUG: " + s);
    }

    // helper to list all gif's
    private static File[] getFilesFromDir(File dir) {
        return dir.listFiles(file -> file.getName().toLowerCase().endsWith("gif"));
    }

    // helper to simplify html file creation a bit without using external libs
    private static class HTMLFile {

        StringBuilder html;

        HTMLFile() {
            html = new StringBuilder();
        }

        HTMLFile append(Object htmlFragment) {
            html.append(String.valueOf(htmlFragment));
            return this;
        }

        StringBuilder addHtmlHeaderStart() {
            return html.append("<!doctype html>\n")
                    .append("<html>\n<head><meta charset='utf-8'>");
        }
    }

    // a index.html
    private static class IndexHtmlFile extends HTMLFile {

        File parentDir = null;

        IndexHtmlFile() {
            super();
            addHtmlHeaderStart()
                    .append("<style>\n")
                    .append("  .cc_color { color: white; }\n")
                    .append("  .cc_img, .cc_colortable { width: 50%; display: inline-block; float: left; }\n")
                    .append("  .cc_img img { max-width: 100%; }\n")
                    .append("</style></head>\n<body>");
        }

        void appendFile(File file) {
            append("<a href='").append(file.getName()).append("'>").append(file.getName()).append("</a>")
                    .append(" ").append(file.length() / 1024).append(" kB").append(" ")
                    .append(new Date(file.lastModified()))
                    .append("<br>");
            if (parentDir == null) parentDir = file.getParentFile();
        }

        File write() throws FileNotFoundException, UnsupportedEncodingException {
            html.append("</body></html>");
            File outFile = new File(parentDir, "index.html");
            debug("Write to " + outFile.getAbsolutePath());
            PrintWriter out = new PrintWriter(outFile, "UTF-8");
            out.print(html.toString());
            out.close();
            return outFile;
        }

    }

    // the html for one image
    private static class OneImageFile extends HTMLFile {

        OneImageFile() {
            super();
            addHtmlHeaderStart()
                    .append("<style>\n")
                    .append("  .cc_color { color: white; }\n")
                    .append("  .cc_img, .cc_colortable { width: 50%; display: inline-block; float: left; }\n")
                    .append("  .cc_img img { max-width: 100%; }\n")
                    .append("</style></head>\n<body>");
        }


        File write(File imageFile) throws FileNotFoundException, UnsupportedEncodingException {
            html.append("</body></html>");
            File outFile = new File(imageFile.getParent(), imageFile.getName().toLowerCase().replace(".gif", ".html"));
            debug("Write to " + outFile.getAbsolutePath());
            PrintWriter out = new PrintWriter(outFile, "UTF-8");
            out.print(html.toString());
            out.close();
            return outFile;
        }

    }

    // helper to pad with zeroes
    private static String pad(String inString, int tolen) {
        int len = inString.length();
        if (len < tolen) return "00000000".substring(0, tolen - len) + inString;
        else return inString.substring(len - tolen);
    }

    /**
     * Increade the counter for one color, possibly creating the color counter
     * @param colorCount  the array that holds all the color information
     * @param clr the color, whos counter we ant to increase
     */
    private static void increaseCount(HashMap<Byte, Integer> colorCount, byte clr) {
        colorCount.merge(clr, 1, (a, b) -> a + b);
    }
}
