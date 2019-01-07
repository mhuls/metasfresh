/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution *
 * Copyright (C) 2008 SC ARHIPAC SERVICE SRL. All Rights Reserved. *
 * This program is free software; you can redistribute it and/or modify it *
 * under the terms version 2 of the GNU General Public License as published *
 * by the Free Software Foundation. This program is distributed in the hope *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. *
 * See the GNU General Public License for more details. *
 * You should have received a copy of the GNU General Public License along *
 * with this program; if not, write to the Free Software Foundation, Inc., *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA. *
 *****************************************************************************/
package de.metas.impexp.excel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Properties;

import javax.annotation.Nullable;

import org.adempiere.exceptions.AdempiereException;
import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Footer;
import org.apache.poi.ss.usermodel.Header;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.compiere.Adempiere;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.slf4j.Logger;

import com.google.common.annotations.VisibleForTesting;

import de.metas.i18n.IMsgBL;
import de.metas.i18n.ITranslatableString;
import de.metas.i18n.Language;
import de.metas.logging.LogManager;
import de.metas.util.Check;
import de.metas.util.Services;
import de.metas.util.StringUtils;
import lombok.NonNull;
import lombok.Value;

/**
 * Abstract MS Excel Format (xls) Exporter
 *
 * @author Teo Sarca, SC ARHIPAC SERVICE SRL
 */
public abstract class AbstractExcelExporter
{
	/**
	 * Is the current Row a Function Row
	 *
	 * @return true if function row
	 */
	public abstract boolean isFunctionRow(int row);

	/**
	 * Get Columns Count
	 *
	 * @return number of columns
	 */
	public abstract int getColumnCount();

	/**
	 * Get Rows Count
	 *
	 * @return number of rows
	 */
	public abstract int getRowCount();

	/**
	 * Check if column is printed (displayed)
	 *
	 * @param col column index
	 * @return true if is visible
	 */
	public abstract boolean isColumnPrinted(int col);

	/**
	 * Get column header name
	 *
	 * @param col column index
	 * @return header name
	 */
	public abstract String getHeaderName(int col);

	/**
	 * Get cell display type (see {@link DisplayType})
	 *
	 * @param row row index
	 * @param col column index
	 * @return display type
	 */
	public abstract int getDisplayType(int row, int col);

	/**
	 * Get cell value
	 *
	 * @param row row index
	 * @param col column index
	 * @return cell value
	 */
	protected abstract CellValue getValueAt(int row, int col);

	/**
	 * Check if there is a page break on given cell
	 *
	 * @param row row index
	 * @param col column index
	 * @return true if there is a page break
	 */
	public abstract boolean isPageBreak(int row, int col);

	//
	private final Logger logger = LogManager.getLogger(getClass());
	protected final IMsgBL msgBL = Services.get(IMsgBL.class);

	//
	private final ExcelFormat excelFormat;
	private final ExcelExportConstants constants;
	//
	private final Workbook workbook;
	private final DataFormat dataFormat;

	private Font fontHeader = null;
	private Font fontDefault = null;
	private Font fontFunctionRow = null;

	private Language m_lang = null;
	private int m_sheetCount = 0;
	private byte fontCharset = Font.DEFAULT_CHARSET;
	//
	private int m_colSplit = 1;
	private int m_rowSplit = 1;
	/** Styles cache */
	private final HashMap<CellStyleKey, CellStyle> cellStyles = new HashMap<>();

	private boolean executed = false;

	public AbstractExcelExporter(
			@Nullable final ExcelFormat excelFormat,
			@Nullable final ExcelExportConstants constants)
	{
		this.excelFormat = excelFormat != null ? excelFormat : ExcelFormats.getDefaultFormat();
		this.constants = constants != null ? constants : ExcelExportConstants.getFromSysConfig();

		workbook = this.excelFormat.createWorkbook(this.constants.isUseStreamingWorkbookImplementation());
		dataFormat = workbook.createDataFormat();

	}

	@VisibleForTesting
	final Workbook getWorkbook()
	{
		return workbook;
	}

	private final DataFormat getDataFormat()
	{
		return dataFormat;
	}

	public final ExcelFormat getExcelFormat()
	{
		return excelFormat;
	}

	public final String getFileExtension()
	{
		return getExcelFormat().getFileExtension();
	}

	protected Properties getCtx()
	{
		return Env.getCtx();
	}

	private final byte getFontCharset()
	{
		return fontCharset;
	}

	/**
	 * @see Font
	 */
	public AbstractExcelExporter setFontCharset(final byte fontCharset)
	{
		assertNotExecuted();
		this.fontCharset = fontCharset;
		return this;
	}

	protected void setFreezePane(final int colSplit, final int rowSplit)
	{
		assertNotExecuted();
		m_colSplit = colSplit;
		m_rowSplit = rowSplit;
	}

	private static String fixString(final String str)
	{
		// ms excel doesn't support UTF8 charset
		return StringUtils.stripDiacritics(str);
	}

	private String convertBooleanToString(final boolean value)
	{
		final String adLanguage = getLanguage().getAD_Language();
		final ITranslatableString translatable = msgBL.getTranslatableMsgText(value);
		return translatable.translate(adLanguage);
	}

	protected Language getLanguage()
	{
		if (m_lang == null)
		{
			m_lang = Env.getLanguage(getCtx());
		}
		return m_lang;
	}

	public AbstractExcelExporter setLanguage(final Language language)
	{
		assertNotExecuted();
		m_lang = language;
		return this;
	}

	private Font createFont()
	{
		final Font font = getWorkbook().createFont();
		font.setCharSet(getFontCharset());
		return font;
	}

	private Font getHeaderFont()
	{
		if (fontHeader == null)
		{
			fontHeader = createFont();
			fontHeader.setBoldweight(Font.BOLDWEIGHT_BOLD);
		}
		return fontHeader;
	}

	private Font getDefaultFont()
	{
		if (fontDefault == null)
		{
			fontDefault = createFont();
		}
		return fontDefault;
	}

	private Font getFunctionRowFont()
	{
		if (fontFunctionRow == null)
		{
			fontFunctionRow = createFont();
			fontFunctionRow.setBoldweight(Font.BOLDWEIGHT_BOLD);
			fontFunctionRow.setItalic(true);
		}
		return fontFunctionRow;
	}

	/**
	 * Get Excel number format string by given {@link NumberFormat}
	 *
	 * @param df number format
	 * @param isHighlightNegativeNumbers highlight negative numbers using RED color
	 * @return number excel format string
	 */
	private String getNumberFormatString(final NumberFormat df, final boolean isHighlightNegativeNumbers)
	{
		StringBuffer format = new StringBuffer();
		final int integerDigitsMin = df.getMinimumIntegerDigits();
		final int integerDigitsMax = df.getMaximumIntegerDigits();
		for (int i = 0; i < integerDigitsMax; i++)
		{
			if (i < integerDigitsMin)
			{
				format.insert(0, "0");
			}
			else
			{
				format.insert(0, "#");
			}
			if (i == 2)
			{
				format.insert(0, ",");
			}
		}
		final int fractionDigitsMin = df.getMinimumFractionDigits();
		final int fractionDigitsMax = df.getMaximumFractionDigits();
		for (int i = 0; i < fractionDigitsMax; i++)
		{
			if (i == 0)
			{
				format.append(".");
			}
			if (i < fractionDigitsMin)
			{
				format.append("0");
			}
			else
			{
				format.append("#");
			}
		}
		if (isHighlightNegativeNumbers)
		{
			final String f = format.toString();
			format = new StringBuffer(f).append(";[RED]-").append(f);
		}

		//
		logger.trace("NumberFormat: {}", format);

		return format.toString();
	}

	private CellStyle getStyle(final int row, final int col)
	{
		final int displayType = getDisplayType(row, col);
		final CellStyleKey key = CellStyleKey.cell(col, displayType, isFunctionRow(row));

		return cellStyles.computeIfAbsent(key, this::createCellStyle);
	}

	private CellStyle createCellStyle(final CellStyleKey key)
	{
		final int displayType = key.getDisplayType();
		final boolean isHighlightNegativeNumbers = true;

		final CellStyle style = getWorkbook().createCellStyle();

		//
		// Font
		final Font font;
		if (key.isFunctionRow())
		{
			font = getFunctionRowFont();
		}
		else
		{
			font = getDefaultFont();
		}
		style.setFont(font);

		//
		// Border
		style.setBorderLeft((short)1);
		style.setBorderTop((short)1);
		style.setBorderRight((short)1);
		style.setBorderBottom((short)1);

		//
		// Data Format
		if (DisplayType.isDate(displayType))
		{
			final DataFormat dataFormat = getDataFormat();
			style.setDataFormat(dataFormat.getFormat("DD.MM.YYYY"));
		}
		else if (DisplayType.isNumeric(displayType))
		{
			final DecimalFormat df = DisplayType.getNumberFormat(displayType, getLanguage());
			final String format = getNumberFormatString(df, isHighlightNegativeNumbers);

			final DataFormat dataFormat = getDataFormat();
			style.setDataFormat(dataFormat.getFormat(format));
		}

		return style;
	}

	private CellStyle getHeaderStyle(final int col)
	{
		return cellStyles.computeIfAbsent(CellStyleKey.header(col), this::createHeaderCellStyle);
	}

	private CellStyle createHeaderCellStyle(final CellStyleKey key_NOTUSED)
	{
		final Font font = getHeaderFont();

		final CellStyle style = getWorkbook().createCellStyle();
		style.setFont(font);
		style.setBorderLeft((short)2);
		style.setBorderTop((short)2);
		style.setBorderRight((short)2);
		style.setBorderBottom((short)2);
		style.setDataFormat((short)BuiltinFormats.getBuiltinFormat("text"));
		style.setWrapText(true);

		return style;
	}

	private void autoSizeColumnsWidth(final Sheet sheet, final int lastColumnIndex)
	{
		final int maxRowsToAllowCellWidthAutoSize = constants.getMaxRowsToAllowCellWidthAutoSize();
		if (maxRowsToAllowCellWidthAutoSize <= 0
				|| maxRowsToAllowCellWidthAutoSize < sheet.getLastRowNum())
		{
			return;
		}

		for (int colnum = 0; colnum < lastColumnIndex; colnum++)
		{
			sheet.autoSizeColumn(colnum);
		}
	}

	private void closeTableSheet(final Sheet prevSheet, final String prevSheetName, final int colCount)
	{
		if (prevSheet == null)
		{
			return;
		}

		//
		autoSizeColumnsWidth(prevSheet, colCount);

		if (m_colSplit >= 0 || m_rowSplit >= 0)
		{
			prevSheet.createFreezePane(m_colSplit >= 0 ? m_colSplit : 0, m_rowSplit >= 0 ? m_rowSplit : 0);
		}

		if (!Check.isEmpty(prevSheetName, true) && m_sheetCount > 0)
		{
			final int prevSheetIndex = m_sheetCount - 1;
			try
			{
				getWorkbook().setSheetName(prevSheetIndex, prevSheetName);
			}
			catch (final Exception ex)
			{
				logger.warn("Error setting sheet {} name to {}", prevSheetIndex, prevSheetName, ex);
			}
		}
	}

	private Sheet createTableSheet()
	{
		final Sheet sheet = getWorkbook().createSheet();
		formatPage(sheet);
		createHeaderFooter(sheet);
		createTableHeader(sheet);
		m_sheetCount++;
		//
		return sheet;
	}

	private void createTableHeader(final Sheet sheet)
	{
		int colnumMax = 0;

		final Row row = sheet.createRow(0);
		// for all columns
		int colnum = 0;
		for (int col = 0; col < getColumnCount(); col++)
		{
			if (colnum > colnumMax)
			{
				colnumMax = colnum;
			}

			//
			if (isColumnPrinted(col))
			{
				final Cell cell = row.createCell(colnum);
				// header row
				final CellStyle style = getHeaderStyle(col);
				cell.setCellStyle(style);
				final String str = fixString(getHeaderName(col));

				// poi37, poi301 compatibility issue
				cell.setCellValue(str);

				colnum++;
			}	// printed
		}	// for all columns
		// m_workbook.setRepeatingRowsAndColumns(m_sheetCount, 0, 0, 0, 0);
	}

	protected void createHeaderFooter(final Sheet sheet)
	{
		// Sheet Header
		final Header header = sheet.getHeader();
		header.setRight(excelFormat.getCurrentPageMarkupTag() + " / " + excelFormat.getTotalPagesMarkupTag());
		// Sheet Footer
		final Footer footer = sheet.getFooter();
		footer.setLeft(Adempiere.getBrandCopyright());
		footer.setCenter(Env.getHeader(getCtx(), 0));
		final Timestamp now = new Timestamp(System.currentTimeMillis());
		footer.setRight(DisplayType.getDateFormat(DisplayType.DateTime, getLanguage()).format(now));
	}

	protected void formatPage(final Sheet sheet)
	{
		sheet.setFitToPage(true);
		// Print Setup
		final PrintSetup ps = sheet.getPrintSetup();
		ps.setFitWidth((short)1);
		ps.setNoColor(true);
		ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
		ps.setLandscape(false);
	}

	private final void assertNotExecuted()
	{
		if (executed)
		{
			throw new AdempiereException("" + this + " was already executed");
		}
	}

	private final void markAsExecuted()
	{
		assertNotExecuted();
		executed = true;
	}

	/**
	 * Export to given stream
	 *
	 * @param out
	 * @throws IOException
	 */
	public final void export(@NonNull final OutputStream out) throws IOException
	{
		Workbook workbook = exportToWorkbook();
		workbook.write(out);
		out.close();
	}

	@VisibleForTesting
	final Workbook exportToWorkbook()
	{
		markAsExecuted();

		final Workbook workbook = getWorkbook();

		Sheet sheet = createTableSheet();
		String sheetName = null;
		//
		int colnumMax = 0;
		for (int rownum = 0, xls_rownum = 1; rownum < getRowCount(); rownum++, xls_rownum++)
		{
			boolean isPageBreak = false;
			final Row row = sheet.createRow(xls_rownum);
			// for all columns
			int colnum = 0;
			for (int col = 0; col < getColumnCount(); col++)
			{
				if (colnum > colnumMax)
				{
					colnumMax = colnum;
				}
				//
				if (isColumnPrinted(col))
				{
					final Cell cell = row.createCell(colnum);

					// 03917: poi-3.7 doesn't have this method anymore
					// cell.setEncoding(Cell.ENCODING_UTF_16); // Bug-2017673 - Export Report as Excel - Bad Encoding

					//
					// Fetch cell value
					CellValue cellValue;
					try
					{
						cellValue = getValueAt(rownum, col);
					}
					catch (final Exception ex)
					{
						logger.warn("Failed extracting cell value at row={}, col={}. Considering it null.", rownum, col, ex);
						cellValue = null;
					}

					//
					// Update the excel cell
					if (cellValue == null)
					{
						// nothing
					}
					else if (cellValue.isDate())
					{
						cell.setCellValue(cellValue.dateValue());
					}
					else if (cellValue.isNumber())
					{
						cell.setCellValue(cellValue.doubleValue());
					}
					else if (cellValue.isBoolean())
					{
						final CreationHelper creationHelper = workbook.getCreationHelper();

						final String value = convertBooleanToString(cellValue.booleanValue());
						cell.setCellValue(creationHelper.createRichTextString(value));
					}
					else
					{
						final CreationHelper creationHelper = workbook.getCreationHelper();

						final String value = fixString(cellValue.stringValue());	// formatted
						cell.setCellValue(creationHelper.createRichTextString(value));

						final Hyperlink hyperlink = createHyperlinkIfURL(value);
						if (hyperlink != null)
						{
							cell.setHyperlink(hyperlink);
						}
					}
					//
					cell.setCellStyle(getStyle(rownum, col));

					// Page break
					if (isPageBreak(rownum, col))
					{
						isPageBreak = true;
						sheetName = fixString(cell.getRichStringCellValue().getString());
					}
					//
					colnum++;
				}	// printed
			}	// for all columns

			//
			if (xls_rownum >= excelFormat.getLastRowIndex())
			{
				isPageBreak = true;
			}

			//
			// Page Break
			if (isPageBreak)
			{
				closeTableSheet(sheet, sheetName, colnumMax);
				sheet = createTableSheet();
				xls_rownum = 0;
				isPageBreak = false;
			}
		}	// for all rows

		//
		closeTableSheet(sheet, sheetName, colnumMax);

		//
		// Workbook Info
		logger.debug("Exported to workbook: {} sheets, {} styles used", m_sheetCount, cellStyles.size());

		return workbook;
	}

	private Hyperlink createHyperlinkIfURL(@Nullable final String str)
	{
		if (str == null || str.isEmpty())
		{
			return null;
		}

		final String urlStr = str.trim();
		if (urlStr.startsWith("http://")
				|| urlStr.startsWith("https://"))
		{
			try
			{
				new URI(urlStr);
				final Hyperlink hyperlink = getWorkbook().getCreationHelper().createHyperlink(org.apache.poi.common.usermodel.Hyperlink.LINK_URL);
				hyperlink.setAddress(urlStr);
				return hyperlink;
			}
			catch (final URISyntaxException e)
			{
				return null;
			}
		}
		else
		{
			return null;
		}
	}

	public File exportToTempFile()
	{
		final File file;
		try
		{
			file = File.createTempFile("Report_", "." + excelFormat.getFileExtension());
		}
		catch (final IOException ex)
		{
			throw new AdempiereException("Failed creating temporary excel file", ex);
		}

		exportToFile(file);
		return file;
	}

	public void exportToFile(@NonNull final File file)
	{
		try (final FileOutputStream out = new FileOutputStream(file))
		{
			export(out);
		}
		catch (final IOException ex)
		{
			throw new AdempiereException("Failed exporting to " + file, ex);
		}
	}

	@Value
	private static final class CellStyleKey
	{
		public static CellStyleKey header(final int column)
		{
			final int displayType = -1; // N/A
			final boolean functionRow = false;
			return new CellStyleKey("header", column, displayType, functionRow);
		}

		public static CellStyleKey cell(final int column, final int displayType, final boolean functionRow)
		{
			return new CellStyleKey("cell", column, displayType, functionRow);
		}

		private final String type;
		private final int column;
		private final int displayType;
		private final boolean functionRow;

		private CellStyleKey(
				final String type,
				final int column,
				final int displayType,
				final boolean functionRow)
		{
			this.type = type;
			this.column = column;
			this.displayType = displayType;
			this.functionRow = functionRow;
		}
	}
}
