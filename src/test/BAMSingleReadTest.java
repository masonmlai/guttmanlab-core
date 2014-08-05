package test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.util.CloseableIterator;

import org.junit.Before;
import org.junit.Test;

import annotation.Annotation;
import annotation.BlockedAnnotation;
import annotation.Gene;
import annotation.SAMFragment;
import annotation.SingleInterval;
import annotation.Annotation.Strand;
import annotation.io.BEDFileIO;
import annotationcollection.AnnotationCollection;
import annotationcollection.BAMSingleReadCollection;
import coordinatespace.CoordinateSpace;

public class BAMSingleReadTest {

	private BAMSingleReadCollection bam;
	private SAMFileHeader fhead;
	private CoordinateSpace refSpace;
	private String fname;
	private BEDFileIO io;
	private AnnotationCollection<Gene> features;
	
	@Before
	public void setUp() throws IOException
	{
		this.bam = new BAMSingleReadCollection(new File("/storage/shared/CoreTestData/chr19.clean.sorted.bam"));
		this.fhead = bam.getFileHeader(); 
		this.refSpace = new CoordinateSpace(fhead);  
		this.fname = "/storage/shared/CoreTestData/RefSeqStrandTest.bed";
		this.io =  new BEDFileIO("/storage/shared/CoreTestData/refspace.txt"); 
		this.features = io.loadFromFile(fname);
	}

	@Test //Pass
	//Tests that the sortedIterator returns reads that only overlap blocks, and not introns
	public void SortedIteratorBlockTest() {
		
		BlockedAnnotation multi = new BlockedAnnotation();
		BlockedAnnotation single = new  BlockedAnnotation();
		SingleInterval block1 = new SingleInterval("chr19", 30090800, 30090948);
		SingleInterval block2 = new SingleInterval("chr19", 30091691, 30091891);
		SingleInterval block = new SingleInterval("chr19", 30090800, 30091891);
		
		multi.addBlock(block1);
		multi.addBlock(block2);
		single.addBlock(block);
		
		multi.setOrientation(Strand.BOTH);
		single.setOrientation(Strand.BOTH);
		
		CloseableIterator<SAMFragment> multi_iter = bam.sortedIterator(multi, false);
		
		int mcount = 0;
		while(multi_iter.hasNext())
		{
			multi_iter.next();
			mcount++;
		}
		multi_iter.close();
		
		CloseableIterator<SAMFragment> singl_iter = bam.sortedIterator(single, false);

		int scount = 0;
		while(singl_iter.hasNext())
		{
			singl_iter.next();
			scount++;
		}
		singl_iter.close();
		
		System.out.println("mcount: " + mcount + "\nscount: " + scount);
		assertEquals("mcount = 0.", 0,mcount);
		assertEquals("scount = 2.", 2,scount);
	}
	

	@Test
	public void IteratorStrandMatchingTest() throws IOException{
		//System.out.println("\n\nCcdc87 Mapped Reads:");
		CloseableIterator<Gene> iter = features.sortedIterator();

		Annotation a = null;
		while(iter.hasNext()) 
		{
			a = iter.next();
			//b.setOrientation(Strand.BOTH);
			if(a.getName().equals("NM_025741")) //Trpd52l3
				break;
		}
		iter.close();
		
		int count =0;
		CloseableIterator<SAMFragment> f_iter = bam.sortedIterator(a, false);
		while(f_iter.hasNext())
		{
			SAMFragment f = f_iter.next();
			count++;
		}
		
		f_iter.close();
		assertEquals("3 positive read should overlap Trpd52l3.",3,count); 
		
	}
	
	@Test
	public void AnnotationCollectionGetCount() {
		int count = bam.getCount();
		assertEquals(count,2267045);
	}
	

}