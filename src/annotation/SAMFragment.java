package annotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.sf.samtools.Cigar;
import net.sf.samtools.CigarElement;
import net.sf.samtools.CigarOperator;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.TextCigarCodec;

public class SAMFragment extends BlockedAnnotation{

	private SAMRecord record;
	private boolean strandIsFirstOfPair; 
	
	public SAMFragment(SAMRecord record){
		this(record, false);
	}
	
	/**
	 * 
	 * @param record The SAM Record
	 * @param strandIsFirstOfPair Whether to treat the first of pair read as the fragment strand
	 */
	public SAMFragment(SAMRecord record, boolean strandIsFirstOfPair){
		super();
		this.record=record;
		this.strandIsFirstOfPair=strandIsFirstOfPair;
	}
	
	@Override
	public String getName() {
		return record.getReadName();
	}
	
	@Override
	public Iterator<SingleInterval> getBlocks() {
		return blocks().iterator();
	}
	
	private Collection<SingleInterval> blocks(){
		return parseCigar(record.getCigarString(), record.getReferenceName(), record.getAlignmentStart()-1, getOrientation(), getName()); 
	}

	@Override
	public String getReferenceName() {
		return record.getReferenceName();
	}

	/**
	 * Returns the start position of this annotation in our coordinate space
	 * SAM coordinates are 1-based and inclusive whereas all of our objects are 0-based exclusive
	 */
	@Override
	public int getReferenceStartPosition() {
		return record.getAlignmentStart()-1;
	}

	@Override
	public int getReferenceEndPosition() {
		return record.getAlignmentEnd();
	}
	
	/**
	 * Return the SAM Record object
	 * @return Original SAMRecord object
	 */
	public SAMRecord getSamRecord() {
		return record;
	}
	
	
	 /**
     * Populate the blocks from a Cigar string
     * @param cigarString
     * @param chr
     * @param start
	 * @param strand 
     * @return blocks
     */
	private Collection<SingleInterval> parseCigar(String cigarString, String chr, int start, Strand strand, String name) {
    	Cigar cigar = TextCigarCodec.getSingleton().decode(cigarString);
    	List<CigarElement> elements=cigar.getCigarElements();
		
    	Collection<SingleInterval> rtrn=new ArrayList<SingleInterval>();
    	
		int currentOffset = start;
		
		for(CigarElement element: elements){
			CigarOperator op=element.getOperator();
			int length=element.getLength();
			
			//then lets create a block from this
			if(op.equals(CigarOperator.MATCH_OR_MISMATCH)){
				int blockStart=currentOffset;
				int blockEnd=blockStart+length;
				rtrn.add(new SingleInterval(chr, blockStart, blockEnd, strand, name));
				currentOffset=blockEnd;
			}
			else if(op.equals(CigarOperator.N)){
				int blockStart=currentOffset;
				int blockEnd=blockStart+length;
				currentOffset=blockEnd;
			}
			else if(op.equals(CigarOperator.INSERTION) ||  op.equals(CigarOperator.H) || op.equals(CigarOperator.DELETION)|| op.equals(CigarOperator.SKIPPED_REGION)){
				currentOffset+=length;
			}
		}
		
		return rtrn;
	}
	
	@Override
	/**
	 * Use strand info from instantiation
	 */
	public Strand getOrientation() {
		Strand rtrn=Annotation.Strand.POSITIVE;
		if(this.record.getReadNegativeStrandFlag()){rtrn=Annotation.Strand.NEGATIVE;}
		if((this.strandIsFirstOfPair && !this.record.getFirstOfPairFlag()) || (!this.strandIsFirstOfPair && this.record.getFirstOfPairFlag())){rtrn=rtrn.getReverseStrand();}
		return rtrn;
	}
	
	@Override
	public int getNumberOfBlocks() {
		return blocks().size();
	}
	
}