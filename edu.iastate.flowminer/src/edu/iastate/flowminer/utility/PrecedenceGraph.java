package edu.iastate.flowminer.utility;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import edu.iastate.flowminer.log.Log;


/**
 * Represents a precedence graph of work to be done, where there are ordering
 * dependencies between work items. Useful for parallelism where tasks have
 * dependencies.
 * 
 * 1) Add Runnables to the graph 
 * 2) Set up precedence relationships between the Runnables
 * 3) Execute the PrecedenceGraph, optionally with maximal parallelism
 * 
 * @author Tom Deering
 * 
 */
public class PrecedenceGraph {
	/**
	 * A callback interface for monitoring the state of jobs in the PrecedenceGraph.
	 * @author tom
	 *
	 */
	public interface JobStateListener{
		public void jobAdded(Runnable job);
		public void jobStarted(Runnable job);
		public void jobFinished(Runnable job);
	}
	
	private JobStateListener jobStateNotifier = null;
	
	private static int idCounter;
	private final Map<Runnable, WorkItemNode> all = new HashMap<Runnable, WorkItemNode>();
	private Set<WorkItemNode> roots = new HashSet<WorkItemNode>();
	private final Set<WorkItemNode> pendingCompletion = new HashSet<WorkItemNode>();
	
	/**
	 * Constructs a new PrecedenceGraph with no listener for job state changes.
	 */
	public PrecedenceGraph(){}
	
	/**
	 * Constructs a new PrecedenceGraph with the given listener for job state changes.
	 * @param jobStateListener
	 */
	public PrecedenceGraph(JobStateListener jobStateListener){
		jobStateNotifier = new JobStateNotifier(jobStateListener);
	}
	
	/**
	 * Add a new item to the PrecedenceGraph.
	 * 
	 * @param toAdd
	 */
	public synchronized void add(final Runnable toAdd) {
		WorkItemNode node = new WorkItemNode(toAdd);
		roots.add(node);
		all.put(toAdd, node);
		if(jobStateNotifier != null) jobStateNotifier.jobAdded(toAdd);
	}

	/**
	 * Add a precedence relation between two items. The ancestor must be run
	 * before the child.
	 * 
	 * @param ancestor
	 * @param child
	 */
	public synchronized void addPrecedence(Runnable ancestor,
			Runnable child) {
		WorkItemNode childNode = all.get(child);
		WorkItemNode ancestorNode = all.get(ancestor);
		ancestorNode.children.add(childNode);
		childNode.ancestors.add(ancestorNode);
		roots.remove(childNode);
	}

	/**
	 * Mark an item has having been completed.
	 * 
	 * @param completed
	 */
	private void markCompleted(WorkItemNode completed) {
		synchronized(all){
			pendingCompletion.remove(completed);
			for (WorkItemNode child : completed.children) {
				child.ancestors.remove(completed);
				if (child.ancestors.size() == 0) roots.add(child);
			}
			all.notifyAll();
		}
		
		completed.ancestors.clear();
		completed.children.clear();
	}

	/**
	 * Executes this PrecedenceGraph of tasks, either single or multithreaded.
	 * 
	 * @param multithreaded
	 * @throws Throwable 
	 */
	public synchronized void execute(boolean multithreaded) throws Throwable {
		initDescendentCounts();
		roots = new TreeSet<WorkItemNode>(roots);
		WorkItemNode nextItem;
		if (multithreaded) {
			LinkedList<Future<?>> submitted = new LinkedList<Future<?>>();
			while ((nextItem = blockForNextItem()) != null) {
				submitted.add(ThreadPool.submitRunnables(nextItem)[0]);
			}
			ThreadPool.blockUntilAllComplete(true, submitted);
		} else {
			while ((nextItem = blockForNextItem()) != null) {
				nextItem.run();
			}
		}
		roots = new HashSet<WorkItemNode>();
	}

	/**
	 * Walk the precedence graph, building up counts of the number of descendents.
	 * That value is stored as a field of each node for use in by sorted root set.
	 * @throws Throwable 
	 */
	private void initDescendentCounts() throws Throwable{
		List<Runnable> initJobs = new LinkedList<Runnable>();
		
		for(final WorkItemNode win : all.values()){
			initJobs.add(new Runnable(){
				@Override
				public void run() {
					win.initDescendents();
				}	
			});
		}
		
		ThreadPool.submitRunnablesBlocking(true, initJobs);
	}
	
	/**
	 * Returns the next available item that can be worked on, blocking to wait
	 * for one to become ready if necessary. If there is no next available item,
	 * then returns null.
	 * 
	 * @return
	 */
	private WorkItemNode blockForNextItem() {
		synchronized(all){
			while(true) {	
				if (!roots.isEmpty()) {
					WorkItemNode next = ((TreeSet<WorkItemNode>) roots).pollLast();
					all.remove(next.thisItem);
					pendingCompletion.add(next);
					return next;
				} else if(pendingCompletion.isEmpty()){
					if(!all.isEmpty()) 
						throw new RuntimeException("Precedence graph with cyclical dependencies could not be executed!");

					return null;
				}
				
				try {
					all.wait(500);
				} catch (InterruptedException e) { Log.error("Problem encountered in ThreadPool thread.", e); }
			}
		}
	}

	/**
	 * An item in the PrecedenceGraph.
	 * 
	 * @author Tom Deering
	 * 
	 */
	private class WorkItemNode implements Runnable, Comparable<WorkItemNode> {
		private int id;
		private Runnable thisItem;
		private int descendents = Integer.MIN_VALUE;
		private Set<WorkItemNode> ancestors = new HashSet<WorkItemNode>();
		private Set<WorkItemNode> children = new HashSet<WorkItemNode>();

		public WorkItemNode(Runnable item) {
			this.id = idCounter++;
			thisItem = item;
		}

		@Override
		public void run() {
			if(jobStateNotifier != null) jobStateNotifier.jobStarted(thisItem);
			
			try{
				thisItem.run();
			}catch(Throwable t){ Log.error("ThreadPool thread encountered exception", t); }
 			PrecedenceGraph.this.markCompleted(this);
 			
 			if(jobStateNotifier != null) jobStateNotifier.jobFinished(thisItem);
		}
		
		private void initDescendents(){
			Set<WorkItemNode> descendentSet = new HashSet<WorkItemNode>(children);
			for(WorkItemNode child : children) descendentWalk(child, descendentSet);
			descendents = descendentSet.size();
		}
		
		private void descendentWalk(WorkItemNode current, Set<WorkItemNode> descendentSet){
			for(WorkItemNode win : current.children){
				if(!descendentSet.contains(win)){
					descendentSet.add(win);
					descendentWalk(win, descendentSet);
				}
			}
		}
		
		@Override
		public int compareTo(WorkItemNode arg0) {
			int comparison = this.descendents - arg0.descendents;
			return comparison == 0 ? this.id - arg0.id : comparison;
		}
	}
	
	private class JobStateNotifier implements JobStateListener{
		JobStateListener listener;
		AdditionNotifier additionNotifier;
		StartNotifier startNotifier;
		FinishNotifier finishNotifier;
		
		public JobStateNotifier(JobStateListener listener){
			this.listener = listener;
			additionNotifier = new AdditionNotifier();
			startNotifier = new StartNotifier();
			finishNotifier = new FinishNotifier();
		}
		
		@Override
		public void jobAdded(Runnable job) {
			additionNotifier.addToQueue(job);
		}

		@Override
		public void jobStarted(Runnable job) {
			startNotifier.addToQueue(job);
		}

		@Override
		public void jobFinished(Runnable job) {
			finishNotifier.addToQueue(job);
		}
		
		private abstract class ActionQueueThread<T> implements Runnable{
			LinkedBlockingQueue<T> queue = new LinkedBlockingQueue<T>();
			Thread t;
			
			public ActionQueueThread(){
				t = new Thread(this);
				t.start();
			}
			
			public void addToQueue(T job){
				boolean success = false;
				while(!success){
					try{
						queue.put(job);
						success = true;
					}catch(InterruptedException ie){}
				}
			}
			
			public abstract void takeAction(T t);
			
			@Override
			public void run() {
				T item;
				while(true){
					try {
						item = queue.take();
						
						try{
							takeAction(item);
						}catch(Exception e){}
					} catch (InterruptedException e) {}
				}
			}
		}
		
		private class AdditionNotifier extends ActionQueueThread<Runnable>{
			@Override
			public void takeAction(Runnable job) {
				listener.jobAdded(job);
			}
		}
		
		private class StartNotifier extends ActionQueueThread<Runnable>{
			@Override
			public void takeAction(Runnable job) {
				listener.jobStarted(job);
			}
		}
		
		private class FinishNotifier extends ActionQueueThread<Runnable>{
			@Override
			public void takeAction(Runnable job) {
				listener.jobFinished(job);
			}
		}
	}
}