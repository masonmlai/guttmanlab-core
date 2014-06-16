package pipeline.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import nextgen.core.job.Job;
import nextgen.core.job.JobUtils;
import nextgen.core.job.LSFJob;
import nextgen.core.job.OGSJob;

import org.apache.log4j.Logger;
import org.ggf.drmaa.DrmaaException;
import org.ggf.drmaa.Session;

import pipeline.Scheduler;

import broad.pda.seq.fastq.FastqParser;
import broad.pda.seq.fastq.FastqSequence;

public class FastqUtils {
	
	private static Logger logger = Logger.getLogger(FastqUtils.class.getName());

	
	/**
	 * Divide a fastq file into several smaller fastq files and write to same directory
	 * @param fastq Original fastq file
	 * @param numOutFiles Number of smaller files to write
	 * @return The names of files written
	 * @throws IOException
	 */
	public static Collection<String> divideFastqFile(String fastq, int numOutFiles) throws IOException {
		
		logger.info("");
		logger.info("Dividing " + fastq + " into " + numOutFiles + " smaller files...");
		
		Collection<String> rtrn = new ArrayList<String>();
		
		boolean allExist = true;
		BufferedWriter[] bw = new BufferedWriter[numOutFiles];
		for(int i = 0; i < bw.length; i ++) {
			String file = fastq + "." + i;
			rtrn.add(file);
			if(!new File(file).exists()) {
				bw[i] = new BufferedWriter(new FileWriter(file));
				allExist = false;
			}
		}
		
		if(allExist) {
			logger.warn("All fastq files already exist. Not regenerating files.");
			for(int i = 0; i < bw.length; i++) {
				if(bw[i] != null) bw[i].close();
			}
			return rtrn;
		}
		
		FastqParser p = new FastqParser();
		p.start(new File(fastq));
		
		int recordNum = 0;
		while(p.hasNext()) {
			FastqSequence r = p.next();
			try {
				r.write(bw[recordNum % bw.length]);
			} catch(NullPointerException e) {
				logger.warn("Null pointer exception; skipping record num " + recordNum);
			}
			recordNum++;
		}
		
		for(int i = 0; i < bw.length; i++) {
			bw[i].close();
		}
		
		logger.info("Done dividing fastq file.");
		
		return rtrn;
		
	}


	/**
	 * Clip sequencing adapters
	 * @param fastxDir Directory containing fastx binaries
	 * @param sampleName Sample name
	 * @param leftFastq Fastq file for read 1
	 * @param rightFastq Fastq file for read 2
	 * @param adapter1 Sequencing adapter for read 1
	 * @param adapter2 Sequencing adapter for read 2
	 * @param fastqReadIdPairNumberDelimiter Delimiter between read name and read number (1 or 2) or null if whitespace
	 * @param scheduler Scheduler
	 * @param drmaaSession Active DRMAA session. Pass null if not using OGS. There should only be one active session at a time. Session should have been created in the main method of the class calling this method. 
	 * @param fastqUtilsJar Path of FastqUtils.jar
	 * @return Collection of clipped fastq file(s). The list is either the clipped read1 file if reads are unpaired, or clipped read1 and clipped read2 if reads paired
	 * @throws InterruptedException 
	 * @throws IOException 
	 * @throws DrmaaException 
	 */
	public static ArrayList<String> clipAdapters(String fastxDir, String sampleName, String leftFastq, String rightFastq, String adapter1, String adapter2, String fastqReadIdPairNumberDelimiter, Scheduler scheduler, Session drmaaSession, String fastqUtilsJar) throws IOException, InterruptedException, DrmaaException {
		Map<String, String> leftFastqs = new TreeMap<String, String>();
		Map<String, String> rightFastqs = new TreeMap<String, String>();
		leftFastqs.put(sampleName, leftFastq);
		rightFastqs.put(sampleName, rightFastq);
		return clipAdapters(fastxDir, leftFastqs, rightFastqs, adapter1, adapter2, fastqReadIdPairNumberDelimiter, scheduler, drmaaSession, fastqUtilsJar).values().iterator().next();
	}


	/**
	 * Clip sequencing adapters
	 * @param fastxDir Directory containing fastx binaries
	 * @param leftFastqs Map of sample name to read 1 fastq file
	 * @param rightFastqs Map of sample name to read 2 fastq file or null if all samples are single end
	 * @param adapter1 Sequencing adapter for read 1
	 * @param adapter2 Sequencing adapter for read 2
	 * @param fastqReadIdPairNumberDelimiter Delimiter between read name and read number (1 or 2) or null if whitespace
	 * @param scheduler Scheduler
	 * @param drmaaSession Active DRMAA session. Pass null if not using OGS. There should only be one active session at a time. Session should have been created in the main method of the class calling this method. 
	 * @param fastqUtilsJar Path of FastqUtils.jar
	 * @return Map of sample name to clipped fastq file(s). The list is either the clipped read1 file if reads are unpaired, or clipped read1 and clipped read2 if reads paired
	 * @throws InterruptedException 
	 * @throws IOException 
	 * @throws DrmaaException 
	 */
	public static Map<String, ArrayList<String>> clipAdapters(String fastxDir, Map<String, String> leftFastqs, Map<String, String> rightFastqs, String adapter1, String adapter2, String fastqReadIdPairNumberDelimiter, Scheduler scheduler, Session drmaaSession, String fastqUtilsJar) throws IOException, InterruptedException, DrmaaException {
		
		Map<String, ArrayList<String>> rtrn = new TreeMap<String, ArrayList<String>>();
		
		FastqUtils.logger.info("");
		FastqUtils.logger.info("Trimming sequencing adapters...");
		Map<String, String> outTmpFilesLeft = new TreeMap<String, String>();
		Map<String, String> outClippedFilesLeft = new TreeMap<String, String>();
		Map<String, String> outTmpFilesRight = new TreeMap<String, String>();
		Map<String, String> outClippedFilesRight = new TreeMap<String, String>();
		
		ArrayList<Job> jobs = new ArrayList<Job>();
		
		// Clip left fastq files
		for(String sampleName : leftFastqs.keySet()) {
			String inFile = leftFastqs.get(sampleName);
			String outTmpFile = inFile + ".clipped_tmp";
			String outClippedFile = inFile + ".clipped.fq";
			outTmpFilesLeft.put(sampleName, outTmpFile);
			outClippedFilesLeft.put(sampleName, outClippedFile);
			File finalClipped = new File(outClippedFile);
			if(finalClipped.exists()) {
				FastqUtils.logger.warn("Clipped file " + outClippedFile + " already exists. Not rerunning fastx_clipper.");
				continue;
			}
			File tmpFile = new File(outTmpFile);
			if(!tmpFile.exists()) {
				// Use fastx program fastx_clipper
				String cmmd = fastxDir + "/fastx_clipper -a " + adapter1 + " -Q 33 -n -i " + inFile + " -o " ;
				if(rightFastqs != null) {
					if(rightFastqs.containsKey(sampleName)) {
						cmmd += outTmpFile;
					}
				} else {
					cmmd += outClippedFile;
				}
				FastqUtils.logger.info("Running fastx command: " + cmmd);
				switch(scheduler) {
				case LSF:
					String jobID = Long.valueOf(System.currentTimeMillis()).toString();
					FastqUtils.logger.info("LSF job ID is " + jobID + ".");
					LSFJob lsfJob = new LSFJob(Runtime.getRuntime(), jobID, cmmd, "fastx_clipper_" + jobID + ".bsub", "week", 4);
					lsfJob.submit();
					jobs.add(lsfJob);
					break;
				case OGS:
					if(drmaaSession == null) {
						throw new IllegalArgumentException("DRMAA session is null. Must provide an active DRMAA session to use OGS. There can only be one active session at a time. Session should have been created in the main method of the class calling this method.");
					}
					OGSJob ogsJob = new OGSJob(drmaaSession, cmmd, "fastx_clip_adapters", null);
					ogsJob.submit();
					FastqUtils.logger.info("OGS job ID is " + ogsJob.getID() + ".");
					jobs.add(ogsJob);
					break;
				default:
					throw new IllegalArgumentException("Scheduler " + scheduler.toString() + " not supported.");
				}
			} else {
				FastqUtils.logger.warn("Temp clipped file " + outTmpFile + " already exists. Not rerunning fastx_clipper. Starting from temp file.");
			}
		}
		
		// Clip right fastq files
		for(String sampleName : leftFastqs.keySet()) {
			if(rightFastqs != null) {
				if(rightFastqs.containsKey(sampleName)) {
					String inFile = rightFastqs.get(sampleName);
					String outTmpFile = inFile + ".clipped_tmp";
					String outClippedFile = inFile + ".clipped.fq";
					outTmpFilesRight.put(sampleName, outTmpFile);
					outClippedFilesRight.put(sampleName, outClippedFile);
					File finalClipped = new File(outClippedFile);
					if(finalClipped.exists()) {
						FastqUtils.logger.warn("Clipped file " + outClippedFile + " already exists. Not rerunning fastx_clipper.");
						continue;
					}
					File tmpFile = new File(outTmpFile);
					if(!tmpFile.exists()) {
						// Use fastx program fastx_clipper
						String cmmd = fastxDir + "/fastx_clipper -a " + adapter2 + " -Q 33 -n -i " + inFile + " -o " + outTmpFile;
						FastqUtils.logger.info("Running fastx command: " + cmmd);
						switch(scheduler) {
						case LSF:
							String jobID = Long.valueOf(System.currentTimeMillis()).toString();
							FastqUtils.logger.info("LSF job ID is " + jobID + ".");
							LSFJob lsfJob = new LSFJob(Runtime.getRuntime(), jobID, cmmd, "fastx_clipper_" + jobID + ".bsub", "week", 4);
							lsfJob.submit();
							jobs.add(lsfJob);
							break;
						case OGS:
							if(drmaaSession == null) {
								throw new IllegalArgumentException("DRMAA session is null. Must provide an active DRMAA session to use OGS. There can only be one active session at a time. Session should have been created in the main method of the class calling this method.");
							}
							OGSJob ogsJob = new OGSJob(drmaaSession, cmmd, "fastx_clip_adapters", null);
							ogsJob.submit();
							FastqUtils.logger.info("OGS job ID is " + ogsJob.getID() + ".");
							jobs.add(ogsJob);
							break;
						default:
							throw new IllegalArgumentException("Scheduler " + scheduler.toString() + " not supported.");
						}
					} else {
						FastqUtils.logger.warn("Temp clipped file " + outTmpFile + " already exists. Not rerunning fastx_clipper. Starting from temp file.");
					}
				}
			}
		}
		
		FastqUtils.logger.info("Waiting for fastx_clipper jobs to finish...");
		JobUtils.waitForAll(jobs);
		
		FastqUtils.logger.info("Done running fastx_clipper.");
		
		ArrayList<Job> filterJobs = new ArrayList<Job>();
		
		for(String sampleName : leftFastqs.keySet()) {
			if(rightFastqs != null) {
				if(rightFastqs.containsKey(sampleName)) {
					if(!rtrn.containsKey(sampleName)) {
						rtrn.put(sampleName, new ArrayList<String>());
					}
					rtrn.get(sampleName).add(outClippedFilesLeft.get(sampleName));
					FastqUtils.logger.info("Current left fq file for sample " + sampleName + " is " + rtrn.get(sampleName).get(0) + ".");
				}
			}
			String inFastq1 = outTmpFilesLeft.get(sampleName);
			String inFastq2 = outTmpFilesRight.get(sampleName);
			String outFastq1 = outClippedFilesLeft.get(sampleName);
			String outFastq2 = outClippedFilesRight.get(sampleName);
			File file1 = new File(outFastq1);
			File file2 = new File(outFastq1);
			if(file1.exists() && file2.exists()) {
				FastqUtils.logger.warn("Using existing files " + outFastq1 + " and " + outFastq2 + ".");
				if(!rtrn.containsKey(sampleName)) {
					rtrn.put(sampleName, new ArrayList<String>());
				}
				rtrn.get(sampleName).clear();
				rtrn.get(sampleName).add(outFastq1);
				rtrn.get(sampleName).add(outFastq2);
				FastqUtils.logger.info("Current fastq files for sample " + sampleName + " are " + rtrn.get(sampleName).get(0) + " and " + rtrn.get(sampleName).get(1) + ".");
				continue;
			}
			FastqUtils.logger.info("Removing reads from files " + inFastq1 + " and " + inFastq2 + " that are not in both files and writing new files to " + outFastq1 + " and " + outFastq2 + "...");
			
			String cmmd = "java -jar -Xmx30g -Xms20g -Xmn10g " + fastqUtilsJar + " -i1 " + inFastq1 + " -i2 " + inFastq2 + " -o1 " + outFastq1 + " -o2 " + outFastq2 + " -f true ";
			if(fastqReadIdPairNumberDelimiter != null) {
				cmmd += " -d " + fastqReadIdPairNumberDelimiter;
			}
			FastqUtils.logger.info("Running command: " + cmmd);
			switch(scheduler) {
			case LSF:
				String jobID = Long.valueOf(System.currentTimeMillis()).toString();
				FastqUtils.logger.info("LSF job ID is " + jobID + ".");
				LSFJob lsfJob = new LSFJob(Runtime.getRuntime(), jobID, cmmd, "filter_fastq_" + jobID + ".bsub", "week", 32);
				lsfJob.submit();
				filterJobs.add(lsfJob);
				break;
			case OGS:
				if(drmaaSession == null) {
					throw new IllegalArgumentException("DRMAA session is null. Must provide an active DRMAA session to use OGS. There can only be one active session at a time. Session should have been created in the main method of the class calling this method.");
				}
				OGSJob ogsJob = new OGSJob(drmaaSession, cmmd, "filter_fastq", null);
				ogsJob.submit();
				FastqUtils.logger.info("OGS job ID is " + ogsJob.getID() + ".");
				filterJobs.add(ogsJob);
				break;
			default:
				throw new IllegalArgumentException("Scheduler " + scheduler.toString() + " not supported.");
			}
			
			
		}
		
		FastqUtils.logger.info("Waiting for FastqUtils jobs to finish...");
		JobUtils.waitForAll(filterJobs);
		
		for(String sampleName : leftFastqs.keySet()) {
			String inFastq1 = outTmpFilesLeft.get(sampleName);
			String inFastq2 = outTmpFilesRight.get(sampleName);
			FastqUtils.logger.info("Done writing cleaned fastq files. To save storage, delete temporary files " + inFastq1 + " and " + inFastq2 + ".");
			String outFastq1 = outClippedFilesLeft.get(sampleName);
			String outFastq2 = outClippedFilesRight.get(sampleName);
			rtrn.get(sampleName).clear();
			rtrn.get(sampleName).add(outFastq1);
			rtrn.get(sampleName).add(outFastq2);
			FastqUtils.logger.info("Current fastq files for sample " + sampleName + " are " + rtrn.get(sampleName).get(0) + " and " + rtrn.get(sampleName).get(1) + ".");
		}
		
		return rtrn;
		
	}
	

}