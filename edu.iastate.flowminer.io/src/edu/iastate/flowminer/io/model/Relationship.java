package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.Schema;

public class Relationship extends Element {
	private long origin_id;
	private long dest_id;
	private String schemaType;

	public Relationship(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
		parse();
	}
	
	@Override
	public boolean interpretChild(IProgressMonitor mon, VTDNav vn, String name) throws NavException, ParseException{
		if(super.interpretChild(mon, vn, name)) return true;
		switch (name) {
		case Schema.RELATIONSHIP_DEST_ID:
			dest_id = Long.parseLong(vn.toString(vn.getText()), Schema.RADIX);
			return true;
		case Schema.RELATIONSHIP_ORIGIN_ID:
			origin_id = Long.parseLong(vn.toString(vn.getText()), Schema.RADIX);
			return true;
		case Schema.RELATIONSHIP_SCHEMA_TYPE:
			schemaType = vn.toString(vn.getText());
			return true;
		default:
			return false;
		}
	}

	public Relationship(String name, long origin_id, long dest_id, String type) {
		super(name);
		this.origin_id = origin_id;
		this.dest_id = dest_id;
		this.schemaType = type;
	}

	public long getOrigin_id() {
		return origin_id;
	}

	public void setOrigin_id(long origin_id) {
		this.origin_id = origin_id;
	}

	public long getDest_id() {
		return dest_id;
	}

	public void setDest_id(long dest_id) {
		this.dest_id = dest_id;
	}
	
	public String getSchemaType(){
		return schemaType;
	}
	
	public void setSchemaType(String type){
		this.schemaType = type;
	}
	
	@Override
	public void doConvert(StringBuilder sb, String name){
		createNode(sb, Schema.RELATIONSHIP_ORIGIN_ID, Long.toString(origin_id, Schema.RADIX));
		createNode(sb, Schema.RELATIONSHIP_DEST_ID, Long.toString(dest_id, Schema.RADIX));
		createNode(sb, Schema.RELATIONSHIP_SCHEMA_TYPE, schemaType);
	}
	
	@Override
	public int subtreeSize() {
		return 1;
	}
}
