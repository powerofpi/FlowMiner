package edu.iastate.flowminer.io.model;

import java.text.ParseException;
import java.util.Set;

import net.ontopia.utils.CompactHashSet;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.Schema;

public class LibraryElement extends Element {
	private Set<PackageElement> packages = new CompactHashSet<PackageElement>();

	public LibraryElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
		parse();
	}
	
	@Override
	public void releaseMemory(){
		super.releaseMemory();
		packages.clear();
		packages = null;
	}

	@Override
	public boolean interpretChild(IProgressMonitor mon, VTDNav vn, String name) throws NavException, ParseException{
		if(super.interpretChild(mon, vn, name)) return true;
		switch (name) {
		case Schema.LIBRARY_PACKAGE:
			packages.add(new PackageElement(mon, vn));
			return true;
		default:
			return false;
		}
	}
	
	public LibraryElement(long id, String name) {
		super(id, name);
	}

	public LibraryElement(String name) {
		super(name);
	}
	
	public Set<PackageElement> getPackages() {
		return packages;
	}
	
	@Override
	public void doConvert(StringBuilder sb, String name){
		for (PackageElement e : packages) {
			e.convert(sb, Schema.LIBRARY_PACKAGE);
		}
	}
	
	@Override
	public int subtreeSize() {
		int size = 1;
		for(XMLConvertable dc : packages) size += dc.subtreeSize();
		return size;
	}
}
