package dk.netarkivet.research;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.netarkivet.research.cdx.CDXEntry;
import dk.netarkivet.research.cdx.CDXExtractor;
import dk.netarkivet.research.cdx.CDXFileWriter;
import dk.netarkivet.research.cdx.DabCDXExtractor;
import dk.netarkivet.research.exception.ArgumentCheck;
import dk.netarkivet.research.harvestdb.HarvestJobExtractor;
import dk.netarkivet.research.harvestdb.HarvestJobInfo;
import dk.netarkivet.research.harvestdb.NasHarvestJobExtractor;
import dk.netarkivet.research.http.HttpRetriever;
import dk.netarkivet.research.interval.CsvUrlIntervalReader;
import dk.netarkivet.research.interval.UrlInterval;
import dk.netarkivet.research.utils.CDXUtils;
import dk.netarkivet.research.utils.DateUtils;
import dk.netarkivet.research.wid.CsvWidReader;
import dk.netarkivet.research.wid.WID;

/**
 * Extracts metadata for the entries of a NAS WID file.
 * The NAS WID file is a CSV file converted from the search-extract Excel document by ELZI.
 * 
 * The input CSV file must contain WIDs in the format of WPIDs and WaybackWIDs.
 * 
 * This tool can either extract the CDX indices for the WIDs, 
 * or a metadata CSV file which will contain both the information from the CDX entry for each WID, 
 * along with the metadata for the harvest job for the resource of the WID.
 */
public class ExtractMetadata {
	/** The log.*/
	private static Logger logger = LoggerFactory.getLogger(ExtractMetadata.class);
	
	/**
	 * Main method.
	 * @param args The list of arguments.
	 */
    public static void main(String ... args) {
    	if(args.length < 4) {
    		System.err.println("Not enough arguments. Requires the following arguments:");
    		System.err.println(" 1. the CSV file in either the NAS WID format, or the URL interval format.");
    		System.err.println("  - NAS WID format has coloumns: 'W/X';#;url;date;location;filename");
    		System.err.println("  - URL interval format has coloumns: 'W';url;earliest date;latest date");
    		System.err.println(" 2. Format for CSV file: either 'WID' or 'URL'");
    		System.err.println(" 3. the base URL to the CDX-server.");
    		System.err.println(" 4. Whether or not to extract harvest job info, either 'y'/'yes' or 'n'/'no'.");
    		System.err.println(" - If this option is set to true, then it requires the parameter: '"
    				+ "dk.netarkivet.settings.file', which will be set by the script using the environment "
    				+ "variable NAS_SETTINGS.");
    		System.err.println(" 5. (OPTIONAL) Whether to extract in CSV format or CDX format, either 'cdx' or 'csv'.");
    		System.err.println(" - This cannot be set to 'cdx' and still extract the harvest job info.");
    		System.err.println(" - The CDX format will be a classical NAS CDX file.");
    		System.err.println(" - Default is 'CSV'.");
    		System.err.println(" 6. (OPTIONAL) the location for the output metadata file.");
    		
    		System.exit(-1);
    	}
    	
    	File csvFile = new File(args[0]);
    	if(!csvFile.isFile()) {
    		throw new IllegalArgumentException("The CSV file '" + csvFile.getAbsolutePath() + "' is not a valid file "
    				+ "(either does not exists or is a directory)");
    	}
    	
    	InputFormat inputFormat = extractInputFormat(args[1]);
    	
    	String cdxServerBaseUrl = args[2];
    	try {
    		new URL(cdxServerBaseUrl);
    	} catch (IOException e) {
    		throw new IllegalArgumentException("The CSX Server url '" + cdxServerBaseUrl + "' is invalid.", e);
    	}
    	DabCDXExtractor cdxExtractor = new DabCDXExtractor(cdxServerBaseUrl, new HttpRetriever());
    	
    	HarvestJobExtractor jobExtractor = null;
    	if(extractWhetherToUseHarvestDb(args[3])) {
    		logger.debug("Using NAS harvest job database for extracting job info");
    		if(System.getProperty("dk.netarkivet.settings.file") == null) {
    			throw new IllegalArgumentException("No system property for the NAS settings file defined. "
    					+ "Must be defined in 'dk.netarkivet.settings.file'.");
    		}
    		if(!(new File(System.getProperty("dk.netarkivet.settings.file")).isFile())) {
    			throw new IllegalArgumentException("The NAS settings file is no a valid file "
    					+ "(either not existing, or a directory)");
    		}
    		jobExtractor = new NasHarvestJobExtractor();
    	}
    	
    	OutputFormat outputFormat = OutputFormat.EXPORT_FORMAT_CSV;
    	if(args.length > 4) {
    		outputFormat = extractOutputFormat(args[4]);
    	}
    	if(outputFormat == OutputFormat.EXPORT_FORMAT_CDX && jobExtractor != null) {
    		throw new IllegalArgumentException("Cannot export in CDX format when also extracting the harvest job info. \n"
    				+ "Either turn off the harvest job extraction or change output format.");
    	}
    	
    	File outFile;
    	if(args.length > 5) {
    		outFile = new File(args[5]);
    	} else {
    		outFile = new File(".");
    	}
    	if(outFile.exists()) {
    		System.err.println("The location for the output file is not vacent.");
    		System.exit(-1);
    	}
    	
    	ExtractMetadata extractor = new ExtractMetadata(csvFile, cdxExtractor, jobExtractor, outFile);
    	try {
    		extractor.extractMetadata(inputFormat, outputFormat);
    	} catch (IOException e) {
    		e.printStackTrace(System.err);
    		throw new IllegalStateException("Failed to extract the metadata", e);
    	}
    	
    	System.out.println("Finished");
    	System.exit(0);
    }
    
    /**
     * Extracts the argument for whether or not to extract the job info from the NAS HarvestDb.
     * Throws an exception, if it is not a valid argument.
     * @param arg The argument. Must be 'y'/'yes' or 'n'/'no'.
     * @return True if it starts with 'y', false if it starts with 'n'.
     */
    protected static boolean extractWhetherToUseHarvestDb(String arg) {
    	ArgumentCheck.checkNotNullOrEmpty(arg, "String arg");
    	if(arg.equalsIgnoreCase("y") || arg.equalsIgnoreCase("yes")) {
    		return true;
    	} else if(arg.equalsIgnoreCase("n") || arg.equalsIgnoreCase("no")) {
    		return false;
    	}
    	logger.warn("Not default value for whether or not to extract the job info from the NAS harvest database. "
    			+ "Trying prefix 'y' or 'n'");
    	if(arg.startsWith("y")) {
    		return true;
    	} else if(arg.startsWith("n")) {
    		return false;
    	}
    	
    	throw new IllegalArgumentException("Cannot decipher argument for whether or not to extract the harvest job. "
    			+ "Must be either 'yes' or 'no'.");
    }
    
    /**
     * Extracts the argument for which output format to use.
     * Must be either CDX or CSV. 
     * @param arg The commandline argument.
     * @return Either CDX or CSV.
     */
    protected static OutputFormat extractOutputFormat(String arg) {
    	if(arg.isEmpty()) {
    		return OutputFormat.EXPORT_FORMAT_CSV;
    	}
    	if(arg.equalsIgnoreCase(EXPORT_FORMAT_CSV)) {
    		return OutputFormat.EXPORT_FORMAT_CSV;
    	} else if(arg.equalsIgnoreCase(EXPORT_FORMAT_CDX)) {
    		return OutputFormat.EXPORT_FORMAT_CDX;
    	} 
    	throw new IllegalArgumentException("Output format must be either 'CDX' or 'CSV'");
    }
    
    /**
     * Extracts the argument for which input format to use.
     * Must be either URL or WID. 
     * @param arg The commandline argument.
     * @return Either CDX or CSV.
     */
    protected static InputFormat extractInputFormat(String arg) {
    	if(arg.equalsIgnoreCase(INPUT_FORMAT_WID)) {
    		return InputFormat.INPUT_FORMAT_WID;
    	} else if(arg.equalsIgnoreCase(INPUT_FORMAT_URL_INTERVAL)) {
    		return InputFormat.INPUT_FORMAT_URL_INTERVAL;
    	} 
    	throw new IllegalArgumentException("Output format must be either '"
    			+ INPUT_FORMAT_URL_INTERVAL + "' or '" + INPUT_FORMAT_WID + "'");
    }
    
    /** The reader of WIDs from the CSV file.*/
    protected final File inputFile;
    /** The base URL for the CDX server.*/
    protected final CDXExtractor cdxExtractor;
    /** The extractor of the harvest job database.*/
    protected final HarvestJobExtractor jobExtractor;
    /** The file where the output is written.*/
    protected final File outFile;
    /** The constants for appending output data to the file.*/
    public static final Boolean APPEND_TO_FILE = true;
    /** The constants for not appending output data to the file.*/
    public static final Boolean NO_APPEND_TO_FILE = false;
    /** Constant for the CDX export format.*/
    public static final String EXPORT_FORMAT_CDX = "CDX";
    /** Constant for the CSV export format.*/
    public static final String EXPORT_FORMAT_CSV = "CSV";
    
    public static final String INPUT_FORMAT_URL_INTERVAL = "URL";
    public static final String INPUT_FORMAT_WID = "WID";
    
    /**
     * Constructor.
     * @param csvFile The input file in the NAS CSV format.
     * @param cdxServer The base url for the CDX server.
     * @param jobExtractor The extractor of harvest job information.
     * @param outFile The output file.
     */
    public ExtractMetadata(File csvFile, CDXExtractor cdxExtractor, HarvestJobExtractor jobExtractor, File outFile) {
    	this.inputFile = csvFile;
    	this.cdxExtractor = cdxExtractor;
    	this.jobExtractor = jobExtractor;
    	this.outFile = outFile;
    }
    
    /**
     * Extracts the metadata for the entries in the CSV file, then extract all the CDX entries for the WIDS,
     * then extract the job data, and finally print.
     * @param inputFormat The input format, either 'WID' or 'URL interval'.
     * @param outputFormat The output format, either 'CDX' or 'CSV'.
     * @throws IOException If it fails to write to file.
     */
    public void extractMetadata(InputFormat inputFormat, OutputFormat outputFormat) throws IOException {
    	Collection<CDXEntry> cdxEntries = extractCdxForFileEntries(inputFormat);
    	
    	if(outputFormat.equals(EXPORT_FORMAT_CSV)) {
    		extractToCsvFormat(cdxEntries);
    	} else {
    		CDXFileWriter outputWriter = new CDXFileWriter(outFile);
    		outputWriter.writeCDXEntries(cdxEntries, DabCDXExtractor.getDefaultCDXFormat());
    	}
    }
    
    /**
     * Extracts the CDX entries for the file of the given type.
     * @param inputFormat The type of file. Either WID or URL interval.
     * @return The CDX entries for the file.
     */
    protected Collection<CDXEntry> extractCdxForFileEntries(InputFormat inputFormat) {
    	if(inputFormat == InputFormat.INPUT_FORMAT_WID) {
    		CsvWidReader reader = new CsvWidReader(inputFile);
    		Collection<WID> wids = reader.extractAllWIDs();
    		return cdxExtractor.retrieveCDXentries(wids);
    	} else {
    		CsvUrlIntervalReader reader = new CsvUrlIntervalReader(inputFile);
    		Collection<UrlInterval> intervals = reader.extractAllUrlIntervals();
    		List<CDXEntry> res = new ArrayList<CDXEntry>(intervals.size());
    		for(UrlInterval ui : intervals) {
        		res.addAll(cdxExtractor.retrieveCDXForInterval(ui));
    		}
    		return res;
    	}
    }
    
    /**
     * Extracts the CDX entries to the CSV metadata format, including extracting the job info.
     * @param cdxEntries The CDX entries.
     * @throws IOException If it fails to print to output file.
     */
    protected void extractToCsvFormat(Collection<CDXEntry> cdxEntries) throws IOException {
    	writeFirstLineToFile();
    	for(CDXEntry entry : cdxEntries) {
    		HarvestJobInfo jobInfo = extractJobInfo(entry);
    		writeEntryToFile(entry, jobInfo);
    	}
    }
    
    /**
     * Writes the first line to the output file, e.g. the output format.
     * @throws IOException If it fails to write to the output file.
     */
    protected void writeFirstLineToFile() throws IOException {
    	try (OutputStream outStream = new FileOutputStream(outFile, NO_APPEND_TO_FILE)) {
    		StringBuilder line = new StringBuilder();
    		
    		line.append("URL;");
    		line.append("Normalized URL;");
    		line.append("Date;");
    		line.append("Content type;");
    		line.append("HTTP Status;");
    		line.append("Checksum;");
    		line.append("Redirect URL;");
    		line.append("Filename;");
    		line.append("File offset;");
    		line.append("Job ID;");
    		line.append("Job Type;");
    		line.append("Job name;");
    		
    		outStream.write(line.toString().getBytes(Charset.defaultCharset()));
    		outStream.flush();
    	}
    }
    
    /**
     * Write the CDX entry and harvest job info to the file.
     * @param entry The CDX entry.
     * @param jobInfo The harvest job info.
     * @throws IOException If there is an issue with writing the file. 
     */
    protected void writeEntryToFile(CDXEntry entry, HarvestJobInfo jobInfo) throws IOException {
    	try (OutputStream outStream = new FileOutputStream(outFile, APPEND_TO_FILE)) {
    		StringBuilder line = new StringBuilder();
    		
    		CDXUtils.addCDXElementToStringBuffer(entry.getUrl(), line);
    		line.append(";");
    		CDXUtils.addCDXElementToStringBuffer(entry.getUrlNorm(), line);
    		line.append(";");
    		CDXUtils.addCDXElementToStringBuffer(DateUtils.dateToWaybackDate(entry.getDate()), line);
    		line.append(";");
    		CDXUtils.addCDXElementToStringBuffer(entry.getContentType(), line);
    		line.append(";");
    		CDXUtils.addCDXElementToStringBuffer(entry.getStatusCode(), line);
    		line.append(";");
    		CDXUtils.addCDXElementToStringBuffer(entry.getDigest(), line);
    		line.append(";");
    		CDXUtils.addCDXElementToStringBuffer(entry.getRedirect(), line);
    		line.append(";");
    		CDXUtils.addCDXElementToStringBuffer(entry.getFilename(), line);
    		line.append(";");
    		CDXUtils.addCDXElementToStringBuffer(entry.getOffset(), line);
    		line.append(";");
    		if(jobInfo != null) {
    			CDXUtils.addCDXElementToStringBuffer(jobInfo.getId(), line);
    			line.append(";");
    			CDXUtils.addCDXElementToStringBuffer(jobInfo.getType(), line);
    			line.append(";");
    			CDXUtils.addCDXElementToStringBuffer(jobInfo.getName(), line);
    			line.append(";");
    		} else {
    			line.append("N/A;");
    			line.append("N/A;");
    			line.append("N/A;");
    			
    		}
    		
    		outStream.write(line.toString().getBytes(Charset.defaultCharset()));
    		outStream.flush();
    	}
    }
    
	/**
	 * Extracts the harvest job info for the harvest job id in the filename in the CDX entry.
	 * @param entry The CDX entry.
	 * @return The harvest job info. Or null if something goes wrong, e.g. malformed filename or missing extractor.
	 */
    protected HarvestJobInfo extractJobInfo(CDXEntry entry) {
		Long jobId = CDXUtils.extractJobID(entry);
		if(jobExtractor == null || jobId == null) {
			logger.debug("Cannot extract harvest job info due to missing jobExtractor or jobId.");
			return null;
		}
		logger.debug("Extracting harvest job info for job '" + jobId + "'.");
		try {
			return jobExtractor.extractJob(jobId);
		} catch (RuntimeException e) {
			logger.warn("Could not extract harvest job info for job '" + jobId + "'", e);
			return null;
		}
    }
    
    /**
     * The types of input formats supported.
     */
    protected enum InputFormat {
    	INPUT_FORMAT_WID,
    	INPUT_FORMAT_URL_INTERVAL;
    };
    /**
     * The type of output formats supported.
     */
    protected enum OutputFormat {
    	EXPORT_FORMAT_CDX,
    	EXPORT_FORMAT_CSV;
    };
}