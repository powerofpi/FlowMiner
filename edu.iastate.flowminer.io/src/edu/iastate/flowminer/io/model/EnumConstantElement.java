package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

public class EnumConstantElement extends VarElement {
	public EnumConstantElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
		parse();
	}

	public EnumConstantElement(long id, String name) {
		super(id, name);
	}

	public EnumConstantElement(String name) {
		super(name);
	}
}
