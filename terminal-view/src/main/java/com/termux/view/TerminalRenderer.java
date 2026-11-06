package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;

public final class TerminalRenderer {

    final int mTextSize;
    final Typeface mTypeface;
    private final Paint mTextPaint = new Paint();

    final float mFontWidth;
    final int mFontLineSpacing;
    private final int mFontAscent;
    final int mFontLineSpacingAndAscent;

    private final float[] asciiMeasures = new float[127];

    public TerminalRenderer(int textSize, Typeface typeface) {
        mTextSize = textSize;
        mTypeface = typeface;

        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);

        mFontLineSpacing = (int) Math.ceil(mTextPaint.getFontSpacing());
        mFontAscent = (int) Math.ceil(mTextPaint.ascent());
        mFontLineSpacingAndAscent = mFontLineSpacing + mFontAscent;
        mFontWidth = mTextPaint.measureText("X");

        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < asciiMeasures.length; i++) {
            sb.setCharAt(0, (char) i);
            asciiMeasures[i] = mTextPaint.measureText(sb, 0, 1);
        }
    }

    public final void render(TerminalEmulator mEmulator, Canvas canvas, int topRow,
                             int selectionY1, int selectionY2, int selectionX1, int selectionX2) {
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + mEmulator.mRows;
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();

        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);

        float heightOffset = mFontLineSpacingAndAscent;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacing;

            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            Rtl.Line layout = Rtl.compute(
                lineObject, columns,
                cursorCol, cursorVisible && row == cursorRow,
                selectionY1, selectionY2, selectionX1, selectionX2,
                row, false);

            Rtl.Cell[] visual = layout.displayed;

            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            boolean lastRunRtl = false;
            int lastRunStartColumn = -1;
            boolean lastRunFontWidthMismatch = false;
            float measuredWidthForRun = 0.f;

            for (int column = 0; column < columns; ) {
                Rtl.Cell cell = visual[column];
                final int codePoint = cell.codePoint;
                final int codePointWcWidth = Math.max(1, cell.displayWidth);
                final long style = cell.style;
                final boolean cellRtl = cell.isRtl;

                if (TextStyle.isTerminalBitmap(style)) {
                    if (lastRunStartColumn >= 0 && column > lastRunStartColumn) {
                        flushRun(canvas, layout, palette, heightOffset,
                            lastRunStartColumn, column,
                            lastRunStyle, lastRunInsideCursor, lastRunInsideSelection, lastRunRtl,
                            measuredWidthForRun, lastRunFontWidthMismatch,
                            cursorShape, reverseVideo);
                    }
                    Bitmap bitmap = mEmulator.getScreen().getSixelBitmap(style);
                    if (bitmap != null) {
                        float left = column * mFontWidth;
                        float top = heightOffset - mFontLineSpacing;
                        Rect src = mEmulator.getScreen().getSixelRect(style);
                        RectF dst = new RectF(left, top, left + mFontWidth, top + mFontLineSpacing);
                        canvas.drawBitmap(bitmap, src, dst, null);
                    }
                    lastRunStyle = style;
                    lastRunInsideCursor = false;
                    lastRunInsideSelection = false;
                    lastRunRtl = false;
                    lastRunStartColumn = column + 1;
                    lastRunFontWidthMismatch = false;
                    measuredWidthForRun = 0.f;
                    column++;
                    continue;
                }

                final boolean insideCursor = cell.insideCursor;
                final boolean insideSelection = cell.insideSelection;

                final float measuredCodePointWidth;
                if (codePoint == 0) {
                    measuredCodePointWidth = 0f;
                } else if (codePoint < asciiMeasures.length && cell.combiningCount == 0 && !cellRtl) {
                    measuredCodePointWidth = asciiMeasures[codePoint];
                } else {
                    int cap = Character.charCount(codePoint) + cell.combiningCount * 2;
                    char[] buf = new char[Math.max(2, cap)];
                    int len = Character.toChars(codePoint, buf, 0);
                    for (int i = 0; i < cell.combiningCount; i++) {
                        len += Character.toChars(cell.combiningChars[i], buf, len);
                    }
                    measuredCodePointWidth = mTextPaint.measureText(buf, 0, len);
                }

                final boolean fontWidthMismatch = !cellRtl
                    && Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth) > 0.01;

                if (style != lastRunStyle
                    || cellRtl != lastRunRtl
                    || insideCursor != lastRunInsideCursor
                    || insideSelection != lastRunInsideSelection
                    || fontWidthMismatch
                    || lastRunFontWidthMismatch) {
                    if (column != 0 && column != lastRunStartColumn) {
                        flushRun(canvas, layout, palette, heightOffset,
                            lastRunStartColumn, column,
                            lastRunStyle, lastRunInsideCursor, lastRunInsideSelection, lastRunRtl,
                            measuredWidthForRun, lastRunFontWidthMismatch,
                            cursorShape, reverseVideo);
                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = style;
                    lastRunInsideCursor = insideCursor;
                    lastRunInsideSelection = insideSelection;
                    lastRunRtl = cellRtl;
                    lastRunStartColumn = column;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                }

                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
            }

            if (columns > lastRunStartColumn && lastRunStartColumn >= 0) {
                flushRun(canvas, layout, palette, heightOffset,
                    lastRunStartColumn, columns,
                    lastRunStyle, lastRunInsideCursor, lastRunInsideSelection, lastRunRtl,
                    measuredWidthForRun, lastRunFontWidthMismatch,
                    cursorShape, reverseVideo);
            }
        }
    }

    private void flushRun(Canvas canvas, Rtl.Line layout, int[] palette, float heightOffset,
                          int startCol, int endCol,
                          long style, boolean insideCursor, boolean insideSelection, boolean isRtl,
                          float measuredWidthForRun, boolean fontWidthMismatch,
                          int cursorShape, boolean reverseVideo) {

        final int runColumns = endCol - startCol;
        if (runColumns <= 0) return;

        Rtl.Cell[] visual = layout.displayed;

        int capacity = 0;
        for (int c = startCol; c < endCol; c++) {
            Rtl.Cell cell = visual[c];
            if (cell.displayWidth == 0 && cell.codePoint == 0) continue;
            if (cell.codePoint != 0) {
                capacity += Character.charCount(cell.codePoint);
                capacity += cell.combiningCount * 2;
            } else {
                capacity += 1;
            }
        }
        if (capacity == 0) capacity = 1;

        char[] buffer = new char[capacity];
        int used = 0;
        for (int c = startCol; c < endCol; c++) {
            Rtl.Cell cell = visual[c];
            if (cell.displayWidth == 0 && cell.codePoint == 0) continue;
            if (cell.codePoint != 0) {
                used += Character.toChars(cell.codePoint, buffer, used);
                for (int i = 0; i < cell.combiningCount; i++) {
                    used += Character.toChars(cell.combiningChars[i], buffer, used);
                }
            } else {
                buffer[used++] = ' ';
            }
        }

        if (isRtl && used > 1) {
            char[] reversed = new char[used];
            int r = 0;
            int i = used - 1;
            while (i >= 0) {
                char c = buffer[i];
                if (i > 0 && Character.isLowSurrogate(c) && Character.isHighSurrogate(buffer[i - 1])) {
                    reversed[r++] = buffer[i - 1];
                    reversed[r++] = c;
                    i -= 2;
                } else {
                    reversed[r++] = c;
                    i -= 1;
                }
            }
            buffer = reversed;
        }

        float runMeasuredWidth = measuredWidthForRun;
        if (isRtl) {
            final Typeface savedTypeface = mTextPaint.getTypeface();
            mTextPaint.setTypeface(Typeface.DEFAULT);
            runMeasuredWidth = mTextPaint.measureText(buffer, 0, used);
            mTextPaint.setTypeface(savedTypeface);
        }

        drawTextRun(canvas, buffer, used, palette, heightOffset,
            startCol, runColumns, runMeasuredWidth,
            insideCursor, insideSelection,
            cursorShape, style, reverseVideo, isRtl);
    }

    private void drawTextRun(Canvas canvas, char[] text, int textLen, int[] palette, float y,
                             int startColumn, int runWidthColumns, float measuredWidth,
                             boolean insideCursor, boolean insideSelection,
                             int cursorStyle, long textStyle, boolean reverseVideo, boolean isRtl) {

        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }

        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        boolean invertCursorTextColor = false;
        if (insideCursor && cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
            invertCursorTextColor = true;
        }

        final boolean reverseVideoHere = reverseVideo
            || insideSelection
            || invertCursorTextColor
            ^ ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0);

        int drawFore = foreColor;
        int drawBack = backColor;
        if (reverseVideoHere) {
            int tmp = drawFore;
            drawFore = drawBack;
            drawBack = tmp;
        }

        float left = startColumn * mFontWidth;
        float right = left + runWidthColumns * mFontWidth;

        final Typeface originalTypeface = mTextPaint.getTypeface();
        if (isRtl) mTextPaint.setTypeface(Typeface.DEFAULT);

        float textAdvance = measuredWidth;
        if (isRtl && textAdvance <= 0f) {
            textAdvance = mTextPaint.measureText(text, 0, textLen);
        }

        boolean savedMatrix = false;
        if (textAdvance > 0f) {
            float targetWidth = runWidthColumns * mFontWidth;
            float ratio = targetWidth / textAdvance;
            if (Math.abs(ratio - 1.f) > 0.01f) {
                canvas.save();
                canvas.scale(ratio, 1.f);
                left /= ratio;
                right /= ratio;
                savedMatrix = true;
            }
        }

        if (drawBack != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            mTextPaint.setColor(drawBack);
            canvas.drawRect(left, y - mFontLineSpacingAndAscent + mFontAscent, right, y, mTextPaint);
        }

        if (insideCursor) {
            mTextPaint.setColor(palette[TextStyle.COLOR_INDEX_CURSOR]);
            float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.f;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR)
                right -= (((right - left) * 3) / 4.f);
            canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint);
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (drawFore >> 16)) * 2 / 3;
                int green = (0xFF & (drawFore >> 8)) * 2 / 3;
                int blue = (0xFF & drawFore) * 2 / 3;
                drawFore = 0xFF000000 | (red << 16) | (green << 8) | blue;
            }

            mTextPaint.setFakeBoldText(bold);
            mTextPaint.setUnderlineText(underline);
            mTextPaint.setTextSkewX(italic ? -0.35f : 0.f);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(drawFore);

            canvas.drawTextRun(text, 0, textLen, 0, textLen,
                left, y - mFontLineSpacingAndAscent, isRtl, mTextPaint);
        }

        if (savedMatrix) canvas.restore();
        if (isRtl) mTextPaint.setTypeface(originalTypeface);
    }

    public float getFontWidth() {
        return mFontWidth;
    }

    public int getFontLineSpacing() {
        return mFontLineSpacing;
    }
}