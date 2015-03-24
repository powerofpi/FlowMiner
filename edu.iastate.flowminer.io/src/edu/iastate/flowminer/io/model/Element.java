package edu.iastate.flowminer.io.model;

import java.text.ParseException;
import java.util.Set;

import net.ontopia.utils.CompactHashSet;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.Schema;

public abstract class Element extends XMLConvertable {
	private long id;
	private String name;
	private Set<String> tag = new CompactHashSet<String>();
	private Set<Attribute> attr = new CompactHashSet<Attribute>(); 
	private static long idGen;
	private static final Object idGenLock = new Object();
	
	public Element(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
	}
	
	@Override
	public void releaseMemory(){
		name = null;
		for(XMLConvertable c : attr) c.releaseMemory();
		tag = null;
		attr = null;
	}
	
	@Override
	public boolean interpretChild(IProgressMonitor mon, VTDNav vn, String name) throws NavException, ParseException{
		switch (name) {
		case Schema.ELEMENT_NAME:
			this.name = vn.toString(vn.getText());
			return true;
		case Schema.ELEMENT_ID:
			id = Long.parseLong(vn.toString(vn.getText()), Schema.RADIX);
			return true;
		case Schema.ELEMENT_TAG:
			tag.add(vn.toString(vn.getText()));
			return true;
		case Schema.ELEMENT_ATTR:
			attr.add(new Attribute(mon, vn));
			return true;
		default:
			return false;
		}
	}

	public Element(String name) {
		super();
		synchronized(idGenLock){
			this.id = idGen++;
		}
		this.name = name;
	}

	public Element(long id, String name) {
		super();
		this.id = id;
		this.name = name;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public Set<String> getTag(){
		return tag;
	}
	
	public Set<Attribute> getAttr(){
		return attr;
	}
	
	@Override
	public final void convert(StringBuilder sb, String name) {
		startNode(sb, name);
		if(this.name != null) createNode(sb, Schema.ELEMENT_NAME, this.name);
		createNode(sb, Schema.ELEMENT_ID, Long.toString(id, Schema.RADIX));
		
		for (String s : tag) {
			createNode(sb, Schema.ELEMENT_TAG, s);
		}
		
		for(Attribute a : attr){
			a.convert(sb, Schema.ELEMENT_ATTR);
		}
		
		doConvert(sb, name);
		endNode(sb, name);
	}
	
	public abstract void doConvert(StringBuilder sb, String name);
}
