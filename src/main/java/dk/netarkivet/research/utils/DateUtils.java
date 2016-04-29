package dk.netarkivet.research.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.netarkivet.research.cdx.CDXEntry;

/**
 * Simple utility class for converting dates between Java date format and the Wayback date format.
 */
public class DateUtils {
    /** Logging mechanism. */
    private static Logger logger = LoggerFactory.getLogger(DateUtils.class);
    /** The expected format for the date, 2012-04-02T23:52:39Z.*/
    private static final String CSV_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

	/** CDX date format string as specified in the CDX documentation. */
	public static final String CDX_DATE_FORMAT = "yyyyMMddHHmmss";

	/** Basic <code>DateFormat</code> is not thread safe. */
	protected static final ThreadLocal<DateFormat> CDX_DATE_PARSER_THREAD = new ThreadLocal<DateFormat>() {
		@Override
		public DateFormat initialValue() {
			DateFormat dateFormat = new SimpleDateFormat(CDX_DATE_FORMAT);
			dateFormat.setLenient(false);
			dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			return dateFormat;
		}
	};
	
	/**
	 * Converts from date to Wayback date string.
	 * @param date The date to convert from.
	 * @return The Wayback format for the date.
	 */
	public static String dateToWaybackDate(Date date) {
		return CDX_DATE_PARSER_THREAD.get().format(date);
	}
	
	/**
	 * Converts from the Wayback date string to java.utils.Date.
	 * @param date The date string from wayback.
	 * @return The Date.
	 * @throws ParseException If it cannot be parsed.
	 */
	public static Date waybackDateToDate(String date) throws ParseException {
		return CDX_DATE_PARSER_THREAD.get().parse(date);		
	}
	
	/**
	 * Extract the actual date from the date-string. 
	 * @param date The date string to parse.
	 * @return The date, or null if was an empty string or it could not be extracted/had a different format.
	 */
	public static Date extractCsvDate(String date) {
		if(date.trim().isEmpty()) {
			return null;
		}
        try {
            DateFormat formatter = new SimpleDateFormat(CSV_DATE_FORMAT);
            Date d = formatter.parse(date);
            return d;
        } catch (ParseException e) {
            logger.warn("Could not parse the timeout date, '" + date + "' with dateformat '" + CSV_DATE_FORMAT 
                    + "' and default locale", e);
        }
        // Try parsing the date in the system default dateformat.
        try {
            return DateFormat.getDateInstance().parse(date);
        } catch (ParseException e) {
            logger.debug("Could not parse the timeout date, '" + date + "' with the system default dateformat", e);
        }
        return null;
	}
	
	/**
	 * Validates that the date of an CDX entry is in the interval of two dates.
	 * @param entry The CDX entry.
	 * @param earliest The earliest date. May be null, if no lower limit.
	 * @param latest The latest date. May be null, if no upper limit.
	 * @return Whether or not the date of the CDX is in the interval.
	 */
	public static boolean checkDateInterval(CDXEntry entry, Date earliest, Date latest) {
		if(earliest != null && entry.getDate() < earliest.getTime()) {
			return false;
		}
		if(latest != null && entry.getDate() > latest.getTime()) {
			return false;
		}
		return true;
	}
}
