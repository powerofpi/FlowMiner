package edu.iastate.flowminer.io.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import net.ontopia.utils.CompactHashSet;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.Schema;

public class IOModel extends XMLConvertable {
	private String author;
	private Date created;
	private Set<LibraryElement> libraries = new CompactHashSet<LibraryElement>();
	private Set<PrimitiveTypeElement> primitive = new CompactHashSet<PrimitiveTypeElement>();
	private Set<Relationship> relationship = new CompactHashSet<Relationship>();
	private static SimpleDateFormat sdf = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ss");
	
	public IOModel(IProgressMonitor mon, VTDNav vn) throws ParseException, NavException {
		super(mon, vn);
		parse();
	}
	
	@Override
	public void releaseMemory(){
		author = null;
		created = null;
		libraries = null;
		primitive = null;
		relationship = null;
	}

	@Override
	public boolean interpretChild(IProgressMonitor mon, VTDNav vn, String name) throws NavException, ParseException{
		switch (name) {
		case Schema.IOMODEL_LIBRARY:
			libraries.add(new LibraryElement(mon, vn));
			return true;
		case Schema.IOMODEL_PRIMITIVE:
			primitive.add(new PrimitiveTypeElement(mon, vn));
			return true;
		case Schema.IOMODEL_RELATIONSHIP:
			relationship.add(new Relationship(mon, vn));
			return true;
		case Schema.IOMODEL_AUTHOR:
			author = vn.toString(vn.getText());
			return true;
		case Schema.IOMODEL_CREATED:
			created = sdf.parse(vn.toString(vn.getText()));
			return true;
		default:
			return false;
		}
	}
	
	public IOModel(String author, Date created) {
		super();
		this.author = author;
		this.created = created;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public Date getCreated() {
		return created;
	}

	public void setCreated(Date created) {
		this.created = created;
	}

	public Set<LibraryElement> getLibrary() {
		return libraries;
	}

	public Set<PrimitiveTypeElement> getPrimitive() {
		return primitive;
	}

	public Set<Relationship> getRelationship() {
		return relationship;
	}
	
	@Override
	public void convert(StringBuilder sb, String name){
		rootNode(sb, name);
		
		if (author != null) {
			createNode(sb, Schema.IOMODEL_AUTHOR, author);
		}

		if (created != null) {
			createNode(sb, Schema.IOMODEL_CREATED, sdf.format(created));
		}
		
		createNode(sb, Schema.IOMODEL_NUM_ELEMENTS, Long.toString(subtreeSize(), Schema.RADIX));
		
		for (PrimitiveTypeElement e : primitive) {
			e.convert(sb, Schema.IOMODEL_PRIMITIVE);
		}
		
		for (LibraryElement e : libraries) {
			e.convert(sb, Schema.IOMODEL_LIBRARY);
		}

		for (Relationship e : relationship) {
			e.convert(sb, Schema.IOMODEL_RELATIONSHIP);
		}
		
		endNode(sb, name);
	}

	@Override
	public int subtreeSize() {
		int size = 0;
		for(XMLConvertable dc : libraries) size += dc.subtreeSize();
		for(XMLConvertable dc : primitive) size += dc.subtreeSize();
		for(XMLConvertable dc : relationship) size += dc.subtreeSize();
		return size;
	}
}
