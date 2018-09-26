package it.vinmar;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class handles a pool of resource object that can be booked by multiple thread.
 * 
 * @param <T> the type of handled resource
 */
public class ResourceArbiter<T> {

	/**
	 * Factory interface is used to formalize object designed to create/destroy handled resources.
	 * 
	 * @param <T> the type of handled resource
	 */
	public interface Factory<T> {
		
		/**
		 * It creates a new driver according setup
		 * 
		 * @return new instance of handled resource
		 */
		T newResource();
		
		/**
		 * Close and deallocate handled driver
		 * 
		 * @param resource is resource object to be destroyed 
		 */
		void closeResource(T resource);
	}
	
	/**
	 * 
	 */
	private final static Logger logger__ = LoggerFactory.getLogger("root");
	
	/**
	 * It resolve concurrent resource requests
	 */
	private final ReentrantLock lock = new ReentrantLock();

	/**
	 * It tracks resource statuses
	 */
	private Map<T, Boolean> resources = null;

	/**
	 * It tracks ageing of each resource
	 */
	private Map<T, Integer> usageCounter = null;

	/**
	 * Simple constructor, it takes as input list of handled resources.
	 * 
	 * @param items is a list of handled resources generated externally
	 */
	public ResourceArbiter(List<T> items) {
		
		resources = items
						.stream()
						.collect(
								Collectors.<T,T,Boolean,HashMap<T,Boolean>>toMap(
										t -> t, 
										t -> Boolean.TRUE,  
										(a, b) -> Boolean.TRUE, 
										() -> new HashMap<>(items.size()))
								);
		factory = null; // null will disable re-new feature
	}

	/**
	 * It's instance {@code Factory} object used to create/destroy handled resources 
	 */
	private final Factory<T> factory;
	
	/**
	 * It's instance max number of iterations for each resource
	 */
	private Integer maxIterations = Integer.MAX_VALUE;

	/**
	 * Advanced constructor. It takes as input {@code Factory} object that will be used to create/destroy handled resources.
	 * Moreover as input there is number of element to placed into pool and how many iteration each instance must serve before proceed to substitution.
	 * 
	 * @param inFactory is the {@code Factory} object
	 * @param nrElement is number of element to placed into pool
	 * @param inMaxIteration is number of iteration each resource must serve before proceed with substitution
	 */
	public ResourceArbiter(Factory<T> inFactory, Integer nrElement, Integer inMaxIteration) {
		factory = inFactory;
		maxIterations = inMaxIteration;
		
		resources = new HashMap<>(nrElement);
		usageCounter = new HashMap<>(nrElement);

		IntStream.rangeClosed(1, nrElement).forEach(in -> {
			T item = factory.newResource();
			resources.put(item, Boolean.TRUE);
			usageCounter.put(item, maxIterations);
		});
	}

	/**
	 * This method returns {@code Optional} with eventually booked resource
	 * 
     * @return an empty {@code Optional}
	 */
	public Optional<T> get() {

		Optional<T> resp = Optional.empty();

		lock.lock();
		try {
			resp = resources.entrySet().stream()
					.filter(Map.Entry::getValue)
					.map(Map.Entry::getKey)
					.findFirst();
			resp.ifPresent(candidate -> resources.put(candidate, Boolean.FALSE));
		} finally {
			lock.unlock();
		}

		return resp;
	}

	/**
	 * This method frees booked resource
	 * 
	 * @param item is booked object to be free
	 */
	public void free(T item) {

		// only if false
		if (!resources.get(item)) {

			Optional<T> disposeItem = Optional.empty();

			lock.lock();
			try {
				if (usageCounter != null) { // extended
					Integer currCounter = usageCounter.get(item) - 1;

					if (currCounter == 0) // invoke dispose and renew procedure
						disposeItem = Optional.of(item); // leave item in FALSE state
					else { // make item available
						resources.put(item, Boolean.TRUE);
						usageCounter.put(item, currCounter);
					}
				} else // normal
					resources.put(item, Boolean.TRUE);

			} finally {
				lock.unlock();
			}

			disposeItem.ifPresent(item2DisposeRenew -> {
				
				logger__.info("#### Re-new of shareable item.");
				
				T renewItem = factory.newResource();

				lock.lock();
				try {
					resources.remove(item2DisposeRenew);
					usageCounter.remove(item2DisposeRenew);

					factory.closeResource(item2DisposeRenew);

					resources.put(renewItem, Boolean.TRUE);
					usageCounter.put(renewItem, maxIterations);
				} finally {
					lock.unlock();
				}
			});
		}
	}
}