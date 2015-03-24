package edu.iastate.flowminer.schema;

import edu.iastate.flowminer.exporter.Exporter;
import edu.iastate.flowminer.importer.Importer;
import edu.iastate.flowminer.miner.Miner;

public abstract class SummarySchema {
	public abstract Miner getMiner();
	public abstract Importer getImporter(boolean existingIndex);
	public abstract Exporter getExporter();
}
