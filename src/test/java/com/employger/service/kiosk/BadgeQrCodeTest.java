package com.employger.service.kiosk;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The printed badge must scan back to its own code: the SVG's squares are
 * redrawn as pixels and read with ZXing's decoder, as a camera would.
 */
class BadgeQrCodeTest {

    private static final Pattern VIEW_BOX = Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"");
    private static final Pattern SQUARE = Pattern.compile("M(\\d+) (\\d+)h1v1h-1z");
    private static final int PIXELS_PER_MODULE = 8;

    @Test
    void theBadgeSvgScansBackToItsCode() throws Exception {
        String code = BadgeCode.generate().value();

        assertThat(decode(BadgeService.svg(code))).isEqualTo(code);
    }

    private static String decode(String svg) throws Exception {
        Matcher box = VIEW_BOX.matcher(svg);
        assertThat(box.find()).isTrue();

        int width = Integer.parseInt(box.group(1)) * PIXELS_PER_MODULE;
        int height = Integer.parseInt(box.group(2)) * PIXELS_PER_MODULE;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0xFFFFFFFF);

        Matcher square = SQUARE.matcher(svg);
        while (square.find()) {
            int left = Integer.parseInt(square.group(1)) * PIXELS_PER_MODULE;
            int top = Integer.parseInt(square.group(2)) * PIXELS_PER_MODULE;
            for (int y = top; y < top + PIXELS_PER_MODULE; y++) {
                Arrays.fill(pixels, y * width + left, y * width + left + PIXELS_PER_MODULE, 0xFF000000);
            }
        }

        BinaryBitmap image = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        return new QRCodeReader().decode(image).getText();
    }
}
