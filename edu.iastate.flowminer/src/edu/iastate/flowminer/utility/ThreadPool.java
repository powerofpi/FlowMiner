
package edu.iastate.flowminer.utility;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * A wrapped, fixed thread pool for accomplishing tasks in parallel. Allows for
 * both synchronous and asynchronous task execution.
 * 
 * @author Tom Deering
 * 
 */
public abstract class ThreadPool {
	// Don't construct this class
	private ThreadPool() {
	}

	// The wrapped thread pool. We do 2 more than the number of CPUs so that
	// use cases where multiple threads in the pool block don't cause a deadlock.
	private static ThreadPoolExecutor executor = (ThreadPoolExecutor) 
			Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()*2 + 2);
	
	/**
	 * Submits the given runnables for non-blocking execution.
	 * 
	 * @param tasks
	 * @return
	 */
	public static Future<?>[] submitRunnables(Runnable... tasks) {
		List<Runnable> taskList = new LinkedList<Runnable>();
		for (Runnable r : tasks)
			taskList.add(r);
		return submitRunnables(taskList);
	}

	/**
	 * Submits the given runnables for non-blocking execution.
	 * 
	 * @param tasks
	 * @return
	 */
	public static Future<?>[] submitRunnables(List<Runnable> tasks) {
		// Randomize task order, to mitigate worst-case performance problems
		// with
		// concurrent locking
		Collections.shuffle(tasks);

		// Add the tasks
		Future<?>[] futures = new Future<?>[tasks.size()];
		for (int i = 0; i < tasks.size(); i++) {
			futures[i] = executor.submit(tasks.get(i));
		}

		return futures;
	}

	/**
	 * Submits the given runnables for blocking execution.
	 * 
	 * @param tasks
	 * @return
	 * @throws Throwable 
	 */
	public static void submitRunnablesBlocking(boolean throwTaskException, Runnable... tasks) throws Throwable {
		// Submit and wait for the tasks to complete
		blockUntilAllComplete(throwTaskException, submitRunnables(tasks));
	}

	/**
	 * Submits the given runnables for blocking execution.
	 * 
	 * @param tasks
	 * @return
	 * @throws Throwable 
	 */
	public static void submitRunnablesBlocking(boolean throwTaskException, List<Runnable> tasks) throws Throwable {
		// Submit and wait for the tasks to complete
		blockUntilAllComplete(throwTaskException, submitRunnables(tasks));
	}

	/**
	 * Blocks until the runnables for the given Futures complete.
	 * 
	 * Optionally rethrows for issues encountered by the tasks.
	 * 
	 * @param waitFor
	 * @return
	 * @throws Throwable 
	 */
	public static void blockUntilAllComplete(boolean throwTaskException, Future<?>... waitFor) throws Throwable {
		LinkedList<Future<?>> running = new LinkedList<Future<?>>();
		for (Future<?> f : waitFor)
			running.add(f);
		blockUntilAllComplete(throwTaskException, running);
	}

	/**
	 * Blocks until the Runnables for the given Futures complete.
	 * 
	 * Optionally rethrows for issues encountered by the tasks.
	 * 
	 * @param throwTaskException
	 * @param waitFor
	 * @return
	 * @throws Throwable 
	 */
	public static void blockUntilAllComplete(boolean throwTaskException, LinkedList<Future<?>> waitFor) throws Throwable {
		// Wait for tasks to complete
		while (waitFor.size() > 0) {
			Future<?> f = waitFor.poll();

			while (true) {
				synchronized (f) {
					if (f.isDone())
						break;
					try {
						f.get();
					} catch (InterruptedException e) {
					} catch (ExecutionException e) {
						if(throwTaskException) throw e.getCause();
					}
				}
			}
		}
	}

	/**
	 * Gets the number of Runnables in the Executor execution queue.
	 * 
	 * @param tasks
	 * @return
	 */
	public static int numRunnablesInQueue() {
		return executor.getQueue().size();
	}
}