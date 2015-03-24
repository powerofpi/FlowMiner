package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

public class PrimitiveTypeElement extends TypeElement {
	public PrimitiveTypeElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
		parse();
	}

	public PrimitiveTypeElement(long id, String name) {
		super(id, name);
	}

	public PrimitiveTypeElement(String name) {
		super(name);
	}
	
	@Override
	public int subtreeSize() {
		return 1;
	}

	@Override
	public void doConvert(StringBuilder sb, String name) {
	}
}
