package hudson.plugins.nunit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.xml.sax.SAXException;

import hudson.FilePath;
import hudson.Launcher;
import hudson.maven.agent.AbortException;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.junit.JUnitResultArchiver;

/**
 * Class responsible for transforming NUnit to JUnit files and then run
 * them all through the JUnit result archiver.
 * 
 * @author Erik Ramfelt
 */
public class NUnitArchiver implements FilePath.FileCallable<Boolean> {

	private static final long serialVersionUID = 1L;

	private static final String JUNIT_REPORTS_PATH = "temporary-junit-reports";

	private transient final String testResultsPattern;
	
	// Build related objects
	private transient final BuildListener listener;
	private transient final Build<?, ?> build;
	private transient final Launcher launcher;

	private transient NUnitReportTransformer unitReportTransformer;

	public NUnitArchiver(
			Build<?, ?> build, Launcher launcher, BuildListener listener,
			String testResults) throws TransformerException, ParserConfigurationException  {

		this.launcher = launcher;
		this.build = build;
		this.listener = listener;
		this.testResultsPattern = testResults;
		unitReportTransformer = new NUnitReportTransformer();
	}
	
	/** {@inheritDoc} */
	public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
		Boolean retValue = Boolean.FALSE;
		listener.getLogger().println("Transforming NUnit tests results");
		String[] nunitFiles = findNUnitReports(ws);
        File junitOutputPath = new File(ws, JUNIT_REPORTS_PATH);
        junitOutputPath.mkdirs();

    	try {
	        
	        // Transform all NUnit files
	        for (String nunitFileName : nunitFiles) {
	        	//listener.getLogger().println("Transforming " + nunitFileName);
				unitReportTransformer.transform(new FileInputStream(new File(ws, nunitFileName)), junitOutputPath);
	        }
	
	        // Run the JUnit test archiver
	        retValue = performJUnitArchiver();
		} catch (TransformerException te) {
			throw new AbortException("Could not transform the NUnit report. Please report this issue to the plugin author", te);
		} catch (SAXException se) {
			throw new AbortException("Could not read the transformed JUnit report. Please report this issue to the plugin author", se);
		} finally {
			// Delete JUnit report files and temp folder        
	        // listener.getLogger().println("Deleting transformed JUnit results");
	        for (File file : junitOutputPath.listFiles()) {
	        	file.delete();
	        }
	        junitOutputPath.delete();
		}
        
    	return retValue;
	}

	/**
	 * Return all NUnit report files
	 * @param parentPath parent
	 * @return an array of strings
	 */
	private String[] findNUnitReports(File parentPath) {
		FileSet fs = new FileSet();
        Project p = new Project();
        fs.setProject(p);
        fs.setDir(parentPath);
        fs.setIncludes(testResultsPattern);
        DirectoryScanner ds = fs.getDirectoryScanner(p);

        String[] nunitFiles = ds.getIncludedFiles();
        if(nunitFiles.length==0) {
            // no test result. Most likely a configuration error or fatal problem
            throw new AbortException("No NUnit test report files were found. Configuration error?");
        }
		return nunitFiles;
	}
	
	/**
	 * Run all JUnit reports through the JUnit archiver
	 * @return true if the JUnitResultArchiver was successful; false otherwise
	 * @throws IOException thrown if the JUnitResultArchiver.perform() methods throws it
	 */
	private Boolean performJUnitArchiver() throws IOException {
		Boolean retValue = Boolean.TRUE;
		JUnitResultArchiver unitResultArchiver = new JUnitResultArchiver(JUNIT_REPORTS_PATH + "/TEST-*.xml");
		try {
			if (! unitResultArchiver.perform(build, launcher, listener)) {
				retValue = Boolean.FALSE;
			}
		} catch (InterruptedException ie) {
			throw new AbortException("Interrupted", ie);
		}
		return retValue;
	}
}