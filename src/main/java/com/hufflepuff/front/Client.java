package com.hufflepuff.front;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.core.IMap;
import com.hazelcast.mapreduce.*;
import com.hufflepuff.back.*;
import com.hufflepuff.domain.Movie;
import com.hufflepuff.domain.Partners;
import com.hufflepuff.util.Timestamper;

public class Client {

	private static final String MAP_NAME = "imdb";

	private static final int QUERY_1 = 1;
	private static final int QUERY_2 = 2;
	private static final int QUERY_3 = 3;
    private static final int QUERY_4 = 4;

	public static void main(String[] args) throws InterruptedException, ExecutionException
	{
		QueryData data = parseArgs(args);

		/*Conection Configuraciont*/
		String name= System.getProperty("name");
		String pass= System.getProperty("pass");
		if (pass == null)
		{
			pass="dev-pass";
		}
		name = "hufflepuff";
		pass="dev-pass";
		System.out.println(String.format("Connecting with cluster dev-name [%s]", name));

		ClientConfig ccfg= new ClientConfig();
		ccfg.getGroupConfig().setName(name).setPassword(pass);
		
		// no hay descubrimiento automatico, 
		// pero si no decimos nada intentar� usar LOCALHOST
		String addresses= System.getProperty("addresses");
		if (addresses != null)
		{	
			String[] arrayAddresses= addresses.split("[,;]");
			ClientNetworkConfig net= new ClientNetworkConfig();
			net.addAddress(arrayAddresses);
			ccfg.setNetworkConfig(net);
		}
		HazelcastInstance client = HazelcastClient.newHazelcastClient(ccfg);
	
	
		System.out.println(client.getCluster() );
	  
	 		
//ARREGLAR ESTO
	 
//	     Preparar la particion de datos y distribuirla en el cluster a trav�s del IMap
		IMap<String, Movie> myMap = client.getMap(MAP_NAME);
		myMap.clear();
		try
		{
			MovieReader.readMovies(myMap, data.path);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
//
//	    
	 	// Ahora el JobTracker y los Workers!
	    JobTracker tracker = client.getJobTracker("default");

	    // Ahora el Job desde los pares(key, Value) que precisa MapReduce
	    KeyValueSource<String, Movie> source = KeyValueSource.fromMap(myMap);
	    Job<String, Movie> job = tracker.newJob(source);

		System.out.println("Inicio del trabajo map/reduce. " + Timestamper.getTime());
		switch (data.query) {
			case QUERY_1: {
				// Orquestacion de Jobs y lanzamiento
				ICompletableFuture<List<String>> future = job
						.mapper(new MapperQ1())
						.reducer(new ReducerQ1())
						.submit(new CollatorQ1(data.n));
				// Tomar resultado e Imprimirlo
				List<String> actors = future.get();
				System.out.println("Los actores más populares son:");
				for (String actor : actors) {
					System.out.println(actor);
				}
				break;
			}
			case QUERY_2: {
                // Orquestacion de Jobs y lanzamiento
                ICompletableFuture<Map<Integer , List<Movie>>> future = job
                        .mapper(new MapperQ2(data.tope))
                        .reducer(new ReducerQ2())
                        .submit();
                // Tomar resultado e Imprimirlo
                Map<Integer, List<Movie>> rta = future.get();

                for (Entry<Integer, List<Movie>> e : rta.entrySet()) {
                    System.out.println("Las mejores películas del año " + e.getKey() + " (con un score de " + e.getValue().get(0).getMetascoreAsInteger() + ") son:");
                    e.getValue().forEach(movie -> {
						System.out.println(movie.getTitle());
					});
					System.out.println("\n");
                }
                break;
            }
			case QUERY_3: {
				// Orquestacion de Jobs y lanzamiento
				ICompletableFuture<List<Partners>> future = job
						.mapper(new MapperQ3())
						.reducer(new ReducerQ3())
						.submit(new CollatorQ3());
				// Tomar resultado e Imprimirlo
				List<Partners> rta = future.get();
				for(Partners p: rta) {
					System.out.print("Los actores " + p.getActor1() + " y " + p.getActor2() + " actuaron " +
					p.getAppearances() + " veces juntos (");
					Set<String> movies = p.getMovies();
					Iterator<String> iterator = movies.iterator();
					for(int i = 0; i < movies.size(); i ++) {
						if(i < movies.size() - 2) {
							System.out.print(iterator.next() + ", ");
						} else if(i == movies.size() - 2) {
							System.out.print(iterator.next() + " y ");
						} else {
							System.out.print(iterator.next() + ").");
						}
					}
					System.out.println();
				}
				break;
			}
            case QUERY_4: {
                // Orquestacion de Jobs y lanzamiento
                ICompletableFuture<Map<String , List<String>>> future = job
                        .mapper(new MapperQ4())
                        .reducer(new ReducerQ4())
                        .submit();
                // Tomar resultado e Imprimirlo
                Map<String, List<String>> rta = future.get();
                for (Entry<String, List<String>> e : rta.entrySet()) {
                    System.out.println("Los favoritos del director " + e.getKey() + " son:");
                    e.getValue().forEach(actor -> {
                        System.out.println(actor);
                    });
                    System.out.println("\n");
                }
                break;
            }
		}
		System.out.println("Fin del trabajo map/reduce. " + Timestamper.getTime());
		System.exit(0);

	}

	private static QueryData parseArgs(String[] args) {
		int query = -1;
		int n = -1;
		int tope = -1;
		String path = null;
		for(String arg: args) {
			String[] argSplitted = arg.split("=");
			String argName = argSplitted[0].toLowerCase();
			String argValue = argSplitted[1];
			if(argName.equals("query")) {
				query = Integer.parseInt(argValue);
			} else if(argName.equals("n")) {
				n = Integer.parseInt(argValue);
			} else if(argName.equals("tope")) {
				tope = Integer.parseInt(argValue);
			} else if(argName.equals("path")) {
				path = argValue;
			}
		}
		return new QueryData(query, n, tope, path);
	}

	private static class QueryData {
		int query;
		int n;
		int tope;
		String path;

		public QueryData(int query, int n, int tope, String path) {
			this.n = n;
			this.tope = tope;
			this.path = path;
			this.query = query;
		}
	}
}
