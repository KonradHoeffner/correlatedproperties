import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;

/** Takes a list of properties and for each of them determines and writes to a file the list of correlated properties and its frequency.
 * In order to save development time (would need thread safe writing to a single file which is not hard but would need some time to get right),
 * the combined file is not created. Just create it by appending all files in the folder output/correlatedproperties/single.**/
public class CorrelatedProperties
{
	static int x = 0;
	static final int RETRIES = 10;
	//	static Logger log = Logger.getLogger("bla");
	final static String[] ENDPOINTS = {"http://dbpedia.org/sparql","http://live.dbpedia.org/sparql"};
	static final int	THREADS	= 30;
	static int initialWaitMs = 1000;//in ms doubles each time  
	protected static File folderSingle = new File("output/correlatedproperties/single");		
	protected static File folderCombined = new File("output/correlatedproperties/combined");
	static
	{
		folderSingle.mkdirs();
		folderCombined.mkdirs();
	}

	@AllArgsConstructor @EqualsAndHashCode public static class Entry
	{
		public final String resourceURL;
		public final String resourceLabel;
		public final String correlatedResource;
		public final String correlatedResourceLabel;
		public final int frequency;				
	}

	public static void readDifferentEntries()
	{
		//		File f = 
	}

	static String getEndpoint()
	{
		if(x<0) x=0;
		return ENDPOINTS[x++%ENDPOINTS.length];
	}

	static String tsv(Object... x)
	{
		String s = Arrays.toString(x);
//		return s.substring(1, s.length()-1).replaceAll(", ","\t");		
		return s.replaceAll("[\\[\\]\\{\\}]", "").replace(", ","\t");
	}

	/** Determines correlated properties for a specific property and writes it to a file whose filename is determined by the last part of the property.**/
	static class CorrelatedPropertiesCalculator implements Callable<String>
	{
		final String query;
		final String property;
		final String label;
		final int nr;

		public CorrelatedPropertiesCalculator(String property,String label, int nr)
		{
			String query = "SELECT distinct(?p) ?l (COUNT(*) AS ?cnt) WHERE {?s <"+property+"> ?o. ?s ?p ?o. ?p rdfs:label ?l. filter(langmatches(lang(?l),\"en\")). FILTER(STRSTARTS(STR(?p), \"http://dbpedia.org/ontology/\")) } GROUP BY ?p ?l order by (?cnt)";
			this.label=label;
			this.property=property;
			this.query=query;
			this.nr=nr;
		}

		private String writeCorrelatedProperties()
		{
			ResultSet rs = new QueryEngineHTTP(getEndpoint(), query).execSelect();
			StringBuilder sb = new StringBuilder();
			while(rs.hasNext())
			{
				QuerySolution qs = rs.next();
				String otherProperty = qs.getResource("?p").getURI();
				String otherLabel = qs.getLiteral("?l").getLexicalForm();
				int count = qs.getLiteral("?cnt").getInt();				
				sb.append(tsv(property.replace(Defaults.DBO, ""),label,otherProperty.replace(Defaults.DBO, ""),otherLabel,count));
				/*if(rs.hasNext()) */{sb.append('\n');}
			}
			return sb.toString();
		}

		@Override public String call() throws Exception
		{	
			File file = Paths.get(folderSingle.getPath(),property.substring(property.lastIndexOf('/')+1)).toFile();
			if(file.exists()) System.out.println(nr+ " exists, skipping property "+property);
			int waitMs = initialWaitMs;
			for(int retries=0;retries<=RETRIES;retries++)
			{
				try				
				{
					System.out.println(nr+"starting property "+property+" query "+query);
					String s = writeCorrelatedProperties();
					System.out.println(nr+"finished property "+property+" query "+query);
					try(PrintWriter out = new PrintWriter(file))
					{
						out.print(s);
					}
					return s;
				}
				catch(Exception e) 
				{
					e.printStackTrace();
					System.err.println(nr+"retry "+(retries+1)+"after wait of "+waitMs+"ms of query "+query);
					Thread.sleep(waitMs);
					waitMs=waitMs<<1;
				}			
			}
			System.err.println(nr+"no result after "+RETRIES+" retries for property "+property+" query "+query);
			return null;						
		}
	}

	public static void generateCorrelatedProperties() throws FileNotFoundException, InterruptedException, ExecutionException
	{		
		ExecutorService service = Executors.newFixedThreadPool(THREADS);
		try(PrintWriter combinedOut = new PrintWriter(Paths.get(folderCombined.getPath(),"correlatedproperties.tsv").toFile()))
		{
			try(Scanner in = new Scanner(new File("input/objectpropertieslabeldomainrange.tsv")))
			{
				int count = 0;
				while(in.hasNextLine())
				{				
					String[] tokens = in.nextLine().split("\t");
					service.submit(new CorrelatedPropertiesCalculator(tokens[0], tokens[1], ++count));				
				}
				System.out.println("processing "+count+" properties");
			}
			service.awaitTermination(1, TimeUnit.DAYS); // ensure that the printwriter is not closed prematurely
		}

	}

	public static void main(String[] args) throws FileNotFoundException, InterruptedException, ExecutionException
	{
		generateCorrelatedProperties();	
	}
}