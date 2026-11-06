package com.termux.view;

import com.termux.terminal.TerminalRow;
import com.termux.terminal.WcWidth;

import java.text.Bidi;

public final class Rtl {

  public static final class Cell {
    public int codePoint;
    public int displayWidth;
    public long style;
    public boolean insideCursor;
    public boolean insideSelection;
    public int originalColumn;
    public boolean isRtl;
    public int[] combiningChars;
    public int combiningCount;

    public void addCombining(int cp) {
      if (combiningChars == null) {
        combiningChars = new int[4];
      } else if (combiningCount == combiningChars.length) {
        int[] grown = new int[combiningChars.length * 2];
        System.arraycopy(combiningChars, 0, grown, 0, combiningChars.length);
        combiningChars = grown;
      }
      combiningChars[combiningCount++] = cp;
    }
  }

  public static final class Line {
    public final Cell[] displayed;
    public final int[] logicalToVisual;
    public final int[] visualToLogical;
    public final int columns;

    Line(int columns) {
      this.columns = columns;
      this.displayed = new Cell[columns];
      this.logicalToVisual = new int[columns];
      this.visualToLogical = new int[columns];
      for (int i = 0; i < columns; i++) {
        Cell c = new Cell();
        c.originalColumn = i;
        c.codePoint = ' ';
        c.displayWidth = 1;
        this.displayed[i] = c;
        this.logicalToVisual[i] = i;
        this.visualToLogical[i] = i;
      }
    }
  }

  private Rtl() {
  }

  public static Line compute(TerminalRow row, int columns, int cursorCol, boolean cursorVisible,
      int selectionY1, int selectionY2, int selectionX1, int selectionX2, int rowIndex,
      boolean updateStateOnly) {

    if (row == null) {
      return new Line(columns);
    }

    Line cached = (row.mCachedBidiLayout instanceof Line) ? (Line) row.mCachedBidiLayout : null;

    if (cached != null && cached.columns == columns) {
      if (updateStateOnly) {
        refreshState(cached, cursorCol, cursorVisible, selectionY1, selectionY2, selectionX1,
            selectionX2, rowIndex);
        return cached;
      }
      if (isContentUnchanged(row, cached)) {
        refreshState(cached, cursorCol, cursorVisible, selectionY1, selectionY2, selectionX1,
            selectionX2, rowIndex);
        return cached;
      }
    }

    Line line = build(row, columns);
    refreshState(line, cursorCol, cursorVisible, selectionY1, selectionY2, selectionX1, selectionX2,
        rowIndex);

    row.mCachedBidiLayout = line;
    row.mLogicalToVisual = line.logicalToVisual;
    row.mVisualToLogical = line.visualToLogical;
    return line;
  }

  private static boolean isContentUnchanged(TerminalRow row, Line cached) {
    if (cached.displayed.length < 1)
      return false;
    if (row.mLogicalToVisual == null)
      return false;

    int spaceUsed = row.getSpaceUsed();
    int checkCols = Math.min(cached.columns, 8);
    int charIdx = 0;
    for (int i = 0; i < checkCols; i++) {
      int cp = cached.displayed[i].codePoint;
      if (charIdx >= spaceUsed)
        return cp == 0 || cp == ' ';
      char c = row.mText[charIdx];
      int rowCp;
      int advance = 1;
      if (Character.isHighSurrogate(c) && charIdx + 1 < spaceUsed) {
        rowCp = Character.toCodePoint(c, row.mText[charIdx + 1]);
        advance = 2;
      } else {
        rowCp = c;
      }
      if (rowCp != cp)
        return false;
      charIdx += advance;
    }
    return true;
  }

  private static void refreshState(Line line, int cursorCol, boolean cursorVisible, int selectionY1,
      int selectionY2, int selectionX1, int selectionX2, int rowIndex) {

    for (Cell c : line.displayed) {
      c.insideCursor = false;
      c.insideSelection = false;
    }

    if (cursorVisible && cursorCol >= 0) {
      int visualCaret = caretVisualColumn(line, cursorCol);
      if (visualCaret >= 0 && visualCaret < line.columns) {
        line.displayed[visualCaret].insideCursor = true;
      }
    }

    if (rowIndex >= selectionY1 && rowIndex <= selectionY2) {
      int l1, l2;
      if (selectionY1 == selectionY2) {
        l1 = Math.min(selectionX1, selectionX2);
        l2 = Math.max(selectionX1, selectionX2);
      } else if (rowIndex == selectionY1) {
        l1 = selectionX1;
        l2 = line.columns - 1;
      } else if (rowIndex == selectionY2) {
        l1 = 0;
        l2 = selectionX2;
      } else {
        l1 = 0;
        l2 = line.columns - 1;
      }
      l1 = Math.max(0, Math.min(line.columns - 1, l1));
      l2 = Math.max(0, Math.min(line.columns - 1, l2));
      if (l1 > l2) {
        int t = l1;
        l1 = l2;
        l2 = t;
      }

      int v1 = line.logicalToVisual[l1];
      int v2 = line.logicalToVisual[l2];
      int vs = Math.min(v1, v2);
      int ve = Math.max(v1, v2);
      vs = Math.max(0, Math.min(line.columns - 1, vs));
      ve = Math.max(0, Math.min(line.columns - 1, ve));
      for (int v = vs; v <= ve; v++) {
        line.displayed[v].insideSelection = true;
      }
    }
  }

  private static Line build(TerminalRow row, int columns) {
    Line line = new Line(columns);

    Cell[] logical = new Cell[columns];
    for (int i = 0; i < columns; i++) {
      Cell c = new Cell();
      c.originalColumn = i;
      c.codePoint = ' ';
      c.displayWidth = 1;
      logical[i] = c;
    }

    char[] text = row.mText;
    int used = row.getSpaceUsed();
    int charIdx = 0;
    int col = 0;

    while (col < columns && charIdx < used) {
      char c = text[charIdx];
      boolean high = Character.isHighSurrogate(c);
      int cp;
      int advance;
      if (high && charIdx + 1 < used) {
        cp = Character.toCodePoint(c, text[charIdx + 1]);
        advance = 2;
      } else {
        cp = c;
        advance = 1;
      }

      int width = WcWidth.width(cp);
      if (width <= 0)
        width = 1;
      if (width > columns - col)
        width = columns - col;

      Cell cell = logical[col];
      cell.codePoint = cp;
      cell.displayWidth = width;
      cell.style = row.getStyle(col);

      if (width == 2 && col + 1 < columns) {
        Cell second = logical[col + 1];
        second.codePoint = 0;
        second.displayWidth = 0;
        second.style = cell.style;
      }

      charIdx += advance;

      while (charIdx < used) {
        char nc = text[charIdx];
        boolean nHigh = Character.isHighSurrogate(nc);
        int ncp;
        int nAdvance;
        if (nHigh && charIdx + 1 < used) {
          ncp = Character.toCodePoint(nc, text[charIdx + 1]);
          nAdvance = 2;
        } else {
          ncp = nc;
          nAdvance = 1;
        }
        int nw = WcWidth.width(ncp);
        if (nw <= 0) {
          cell.addCombining(ncp);
          charIdx += nAdvance;
        } else {
          break;
        }
      }

      col += width;
    }

    while (col < columns) {
      logical[col].codePoint = ' ';
      logical[col].displayWidth = 1;
      logical[col].style = row.getStyle(col);
      col++;
    }

    for (int i = 0; i < columns; i++) {
      if (logical[i].codePoint != 0 && logical[i].codePoint != ' ') {
        logical[i].isRtl = isRtlCodePoint(logical[i].codePoint);
      }
    }

    int activeEnd = 0;
    for (int i = columns - 1; i >= 0; i--) {
      int cp = logical[i].codePoint;
      if (cp != ' ' && cp != 0) {
        activeEnd = i + 1;
        break;
      }
    }

    if (activeEnd == 0) {
      for (int i = 0; i < columns; i++) {
        line.displayed[i] = logical[i];
        line.logicalToVisual[i] = i;
        line.visualToLogical[i] = i;
      }
      return line;
    }

    boolean anyRtl = false;
    for (int i = 0; i < activeEnd; i++) {
      if (logical[i].isRtl) {
        anyRtl = true;
        break;
      }
    }

    if (!anyRtl) {
      for (int i = 0; i < columns; i++) {
        line.displayed[i] = logical[i];
        line.logicalToVisual[i] = i;
        line.visualToLogical[i] = i;
      }
      return line;
    }

    StringBuilder sb = new StringBuilder(activeEnd);
    int[] sbIndexToLogical = new int[activeEnd * 2];
    int sbLen = 0;
    for (int i = 0; i < activeEnd; i++) {
      Cell c = logical[i];
      int before = sb.length();
      if (c.codePoint == 0) {
        sb.append(' ');
      } else {
        sb.appendCodePoint(c.codePoint);
      }
      int added = sb.length() - before;
      for (int k = 0; k < added; k++) {
        if (sbLen < sbIndexToLogical.length) {
          sbIndexToLogical[sbLen++] = i;
        }
      }
    }

    Bidi bidi;
    try {
      bidi = new Bidi(sb.toString(), Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
    } catch (Exception e) {
      for (int i = 0; i < columns; i++) {
        line.displayed[i] = logical[i];
        line.logicalToVisual[i] = i;
        line.visualToLogical[i] = i;
      }
      return line;
    }

    if (bidi.isLeftToRight()) {
      for (int i = 0; i < columns; i++) {
        line.displayed[i] = logical[i];
        line.logicalToVisual[i] = i;
        line.visualToLogical[i] = i;
      }
      return line;
    }

    byte[] levels = new byte[sbLen];
    Integer[] reorder = new Integer[sbLen];
    for (int i = 0; i < sbLen; i++) {
      levels[i] = (byte) bidi.getLevelAt(i);
      reorder[i] = i;
    }
    Bidi.reorderVisually(levels, 0, reorder, 0, sbLen);

    boolean[] placed = new boolean[columns];
    boolean[] displayedSlotUsed = new boolean[columns];
    int vCol = 0;

    for (int v = 0; v < sbLen; v++) {
      int reorderedSbIdx = reorder[v];
      if (reorderedSbIdx < 0 || reorderedSbIdx >= sbLen)
        continue;
      int logicalIdx = sbIndexToLogical[reorderedSbIdx];
      if (logicalIdx < 0 || logicalIdx >= columns)
        continue;
      if (placed[logicalIdx])
        continue;
      placed[logicalIdx] = true;
      Cell c = logical[logicalIdx];
      int w = Math.max(1, c.displayWidth);
      if (vCol + w > columns)
        break;

      line.displayed[vCol] = c;
      line.visualToLogical[vCol] = logicalIdx;
      line.logicalToVisual[logicalIdx] = vCol;
      displayedSlotUsed[vCol] = true;

      if (w == 2) {
        int secondIdx = Math.min(columns - 1, logicalIdx + 1);
        Cell second = logical[secondIdx];
        if (second.codePoint == 0) {
          placed[secondIdx] = true;
          if (vCol + 1 < columns) {
            line.displayed[vCol + 1] = second;
            line.visualToLogical[vCol + 1] = second.originalColumn;
            line.logicalToVisual[second.originalColumn] = vCol + 1;
            displayedSlotUsed[vCol + 1] = true;
          }
        }
      }
      vCol += w;
    }

    for (int i = 0; i < columns; i++) {
      if (placed[i])
        continue;
      if (vCol >= columns)
        break;
      Cell c = logical[i];
      int w = Math.max(1, c.displayWidth);
      if (vCol + w > columns)
        w = columns - vCol;
      line.displayed[vCol] = c;
      line.visualToLogical[vCol] = i;
      line.logicalToVisual[i] = vCol;
      displayedSlotUsed[vCol] = true;
      vCol += w;
    }

    while (vCol < columns) {
      Cell filler = new Cell();
      filler.codePoint = ' ';
      filler.displayWidth = 1;
      filler.originalColumn = vCol;
      filler.style = row.getStyle(vCol);
      line.displayed[vCol] = filler;
      line.visualToLogical[vCol] = vCol;
      vCol++;
    }

    return line;
  }

  private static int caretVisualColumn(Line line, int logicalCursorCol) {
    if (logicalCursorCol <= 0)
      return 0;
    if (line.columns <= 0)
      return 0;
    int visualCol = Math.min(line.columns - 1, Math.max(0, logicalCursorCol));
    return visualCol;
  }

  private static boolean isRtlCodePoint(int cp) {
    if (cp >= 0x0590 && cp <= 0x05FF)
      return true;
    if (cp >= 0x0600 && cp <= 0x06FF)
      return true;
    if (cp >= 0x0700 && cp <= 0x074F)
      return true;
    if (cp >= 0x0750 && cp <= 0x077F)
      return true;
    if (cp >= 0x08A0 && cp <= 0x08FF)
      return true;
    if (cp >= 0xFB1D && cp <= 0xFB4F)
      return true;
    if (cp >= 0xFB50 && cp <= 0xFDFF)
      return true;
    if (cp >= 0xFE70 && cp <= 0xFEFF)
      return true;
    if (cp >= 0x10800 && cp <= 0x10FFF)
      return true;
    if (cp >= 0x1E800 && cp <= 0x1EFFF)
      return true;
    return false;
  }

  public static int translateVisualToLogical(TerminalRow row, int visualCol, int columns) {
    if (row == null)
      return visualCol;
    if (visualCol < 0)
      return 0;
    if (visualCol >= columns)
      return columns - 1;
    if (row.mVisualToLogical != null && row.mVisualToLogical.length == columns) {
      int l = row.mVisualToLogical[visualCol];
      if (l >= 0 && l < columns)
        return l;
    }
    return visualCol;
  }

  public static int translateLogicalToVisual(TerminalRow row, int logicalCol, int columns) {
    if (row == null)
      return logicalCol;
    if (logicalCol < 0)
      return 0;
    if (logicalCol >= columns)
      return columns - 1;
    if (row.mLogicalToVisual != null && row.mLogicalToVisual.length == columns) {
      int v = row.mLogicalToVisual[logicalCol];
      if (v >= 0 && v < columns)
        return v;
    }
    return logicalCol;
  }
}