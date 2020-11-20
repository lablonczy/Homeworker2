import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedList;

public class Driver {

	public static void main(String[] args) {
		Date date = new Date();
		LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		String month = localDate.getMonth().name();


		LinkedList<Assignment> assns = null;
		try {
			long bbTime = System.currentTimeMillis();
			assns = new BlackboardRetriever().retrieve();
			bbTime = (System.currentTimeMillis() - bbTime)/1000;
			System.out.println("found " + assns.size() + " blackboard assns in " + bbTime + " secs, which is " + (bbTime > 5.0 ? "slow as shit":"alright\n"));


		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		assns.forEach( (assn) -> System.out.println((assn.getDue().toUpperCase().contains(month)?"\n~~~~~~~~~~~ " + assn + "\n":assn)));


	}

}
