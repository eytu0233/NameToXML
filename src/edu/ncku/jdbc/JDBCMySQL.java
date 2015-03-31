package edu.ncku.jdbc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JDBCMySQL extends Observable {

	/*
	 * 將community數值當作key，其名稱為值存入此LinkedHashMap 選擇LinkedHashMap是為了能夠使key進行順序排列
	 */
	private LinkedHashMap<Integer, String> communityTable = new LinkedHashMap<Integer, String>();
	/* 此成員用來將選取到的item_id儲存起來 */
	private ArrayList<Integer> selectedItems = new ArrayList<Integer>();
	/*  */
	private ArrayList<String> collectionNames = new ArrayList<String>();

	/* 此成員為與資料庫連結的實體 */
	private Connection con = null;
	/* 此成員用來處理SQL語法 */
	private Statement stat = null;
	/* 此成員用來接取來自資料庫的資料 */
	private ResultSet rs = null;

	/* 用來取得系所列表的SQL查詢語法 */
	private final String selectCommunitySQL = "SELECT community.community_id, community.name FROM dspace.community Order By community.name";
	/* 用來取得item_id的SQL查詢語法 */
	private String selectItemIDSQL = "SELECT communityitemsbydate.community_id,communityitemsbydate.item_id,communityitemsbydate.date_issued FROM dspace.communityitemsbydate";
	/* 使用getDateTime靜態方法取得當日日期並作為log檔名 */
	private String logFileName = getDateTime() + ".log";

	/* 出現item遺失錯誤的旗標 */
	private boolean errItems = false;
	/* 取得該次搜尋到的資料筆數 */
	private int records = 0;
	/* 已完成轉換的資料筆數 */
	private int finish = 0;

	/* 以下為getter */
	public LinkedHashMap<Integer, String> getCommunityTable() {
		return communityTable;
	}

	public void setErrItems(boolean flag) {
		errItems = flag;
	}

	public boolean isErrItems() {
		return errItems;
	}

	public int getFinish() {
		return finish;
	}

	public int getRecords() {
		return records;
	}

	/**
	 * 功能 : 讀取Config.ini裡的設定，登入對應之資料庫
	 */
	public JDBCMySQL() {

		String ip = "", db = "", id = "", password = "", line = "";

		try {
			/* 掃描過Config.ini文件，取得資料庫的ID、密碼、Table */
			Scanner fc = new Scanner(new File("Config.ini"));
			while (fc.hasNextLine()) {
				line = fc.nextLine();

				if (line.equals("[MYSQL ID]")) {
					id = fc.nextLine();
				} else if (line.equals("[MYSQL PASSWORD]")) {
					password = fc.nextLine();
				} else if (line.equals("[MYSQL IP]")) {
					ip = fc.nextLine();
				} else if (line.equals("[MYSQL SCHEMAS]")) {
					db = fc.nextLine();
				} else if (line.equals("[Collection Name]")) {
					do {
						collectionNames.add(fc.nextLine());
					} while (fc.hasNextLine());
				}
			}
			fc.close();

			Class.forName("com.mysql.jdbc.Driver");
			/*
			 * 註冊driver，jdbc:mysql://localhost/test?useUnicode=true&
			 * characterEncoding=Big5
			 * localhost是主機名，test是database名，useUnicode=true
			 * &characterEncoding=Big5使用的編碼
			 */
			con = DriverManager.getConnection("jdbc:mysql://" + ip + "/" + db
					+ "?useUnicode=true&characterEncoding=Big5", id, password);

			/* 正常連結資料庫後，從資料庫當中取得系所列表 */
			getCommunities();

		} catch (Exception e) {
			/* 一律將例外送進log方法之中 */
			log(e);
		}
	}

	/**
	 * 功能 : 利用時間與系所值的參數取得item id
	 * 
	 * @param startY
	 *            起始年
	 * @param startM
	 *            起始月
	 * @param startD
	 *            起始日
	 * @param endY
	 *            終止年
	 * @param endM
	 *            終止月
	 * @param endD
	 *            終止日
	 * @param community
	 *            目標系所值
	 */
	public void selectItemIDByCommunityAndDate(int startY, int startM,
			int startD, int endY, int endM, int endD, int community) {

		/* 確認資料庫的連結 */
		try {
			if (con == null || con.isClosed()) {
				log(new Exception("Database has been close."));
			}
		} catch (Exception e) {
			log(e);
		}

		String startDateSQL = null, endDateSQL = null, communitySQL = null, selectItemIDSQLQuery = null;

		/* 當起始年的值為0，表示不設定起始日期 */
		if (startY != 0)
			startDateSQL = String.format("date_issued >= \'%d-%02d-%02d\'",
					startY, startM, startD);

		/* 當終止年的值為0，表示不設定終止日期 */
		if (endY != 0)
			endDateSQL = String.format("date_issued <= \'%d-%02d-%02d\'", endY,
					endM, endD);

		/* 系所值可從communityTable當中取得，並將其加入SQL條件 */
		if (community != 0)
			communitySQL = String.format("community_id = %d", community);

		/* 主要動作在於組合前面的三項條件，但由於可能會少其中一個條件故產生以下條件組合 */
		if (startDateSQL != null || endDateSQL != null || communitySQL != null) {
			selectItemIDSQLQuery = selectItemIDSQL + " WHERE ";// 將SQL語法加上條件語法
			if (startDateSQL != null)
				selectItemIDSQLQuery += startDateSQL;
			if (endDateSQL != null)
				selectItemIDSQLQuery += ((startDateSQL != null) ? " And " : "")// 假如已有第一項條件則在此條件前面加And
						+ endDateSQL;
			if (communitySQL != null)
				selectItemIDSQLQuery += ((startDateSQL != null || endDateSQL != null) ? " And "// 假如已有前面的條件則在此條件前面加And
						: "")
						+ communitySQL;
		}

		// System.out.println("Start Select...");
		selectedItems.clear();
		try {
			// System.out.println(selectItemIDSQLQuery);
			stat = con.createStatement();// 產生SQL語法實體
			rs = stat.executeQuery(selectItemIDSQLQuery);// 執行SQL語法並取得表格

			while (rs.next()) {
				// System.out.println("item_id = " + rs.getInt("item_id"));
				selectedItems.add(rs.getInt("item_id"));// 將條件搜尋到的item_is存進selectedItems
			}

			Collections.sort(selectedItems);// 依照數值由小到大排序
		} catch (SQLException e) {
			log(e);
		} finally {
			Close();
		}

		// System.out.println("End Select");
	}

	/**
	 * 
	 * @return 項目是否有遺失
	 */
	public boolean checkItemErr() {
		/* 確認資料庫的連結與搜尋到的item_id數量不可為0 */
		try {
			if (con == null || con.isClosed()) {
				log(new Exception("Database has been close."));
				return true;
			} else if (selectedItems.size() == 0) {// 表示selectItemIDByCommunityAndDate方法沒有搜尋到項目
				log(new Exception("No item is searched."));
				return true;
			}
		} catch (Exception e) {
			log(e);
			return true;
		}

		String SQL = new String();
		String str1 = "Select "
				+ "metadatavalue.item_id, "
				+ "count(*),"
				+ "metadatavalue.metadata_field_id, "
				+ "metadatavalue.text_value, "
				+ "metadatafieldregistry.element, "
				+ "collection.name, "
				+ "community.name As Department "
				+ "From "
				+ "metadatavalue Inner Join "
				+ "metadatafieldregistry On metadatavalue.metadata_field_id = "
				+ "metadatafieldregistry.metadata_field_id Inner Join "
				+ "collection2item On metadatavalue.item_id = collection2item.item_id Inner Join "
				+ "collection On collection2item.collection_id = collection.collection_id "
				+ "Inner Join "
				+ "community2collection On collection.collection_id = "
				+ "community2collection.collection_id Inner Join "
				+ "community On community2collection.community_id = community.community_id "
				+ "Inner Join "
				+ "item On metadatavalue.item_id = item.item_id " + "Where "
				+ "metadatavalue.item_id In (";
		StringBuilder item_ids = new StringBuilder();
		for (Integer item_id : selectedItems) {
			if (item_ids.length() == 0)
				item_ids.append(item_id);
			item_ids.append(", " + item_id);
		}
		item_ids.append(") And ");
		String str2 = "metadatavalue.metadata_field_id In (1, 9, 65, 25, 10) And ";
		String str3 = "";
		for (String collectionName : collectionNames) {
			if (!"".equals(str3))
				str3 += " Or ";
			else
				str3 += "(";
			str3 += String.format("(collection.name = '%s')", collectionName);
		}
		str3 = ") And " + "item.withdrawn = 0 And "
				+ "item.owning_collection <> '' " + "Group By "
				+ "metadatavalue.item_id " + "Having " + "count(*)<>6 "
				+ "Order By " + "metadatavalue.item_id, "
				+ "metadatavalue.metadata_field_id ";
		
		System.out.println(str3);

		/*
		 * 以上原始碼主要是把SQL語法給組合起來 SQL語法的功能即將有缺失的項目給尋找出來，每個項目要有6個rows，故有缺失的項目將會被找出來
		 */
		SQL = str1 + item_ids + str2 + str3;

		LinkedList<Integer> checkItems = new LinkedList<Integer>();

		try {
			stat = con.createStatement();
			rs = stat.executeQuery(SQL);

			while (rs.next()) {
				checkItems.add(rs.getInt("item_id"));
			}
		} catch (Exception e) {
			log(e);
		} finally {
			Close();
		}

		if (checkItems.size() == 0)// 當沒有項目時表示全部都符合規格
			return false;

		/*
		 * 假如有錯誤項目的產生，將其加進原始SQL語法當中抓取項目的資料內容 資料內容為該項目的連結。
		 */
		item_ids = new StringBuilder();
		for (Integer item_id : checkItems) {
			if (item_ids.length() == 0)
				item_ids.append(item_id);
			item_ids.append(", " + item_id);
		}
		selectedItems.clear();
		item_ids.append(") And ");
		str2 = "metadatavalue.metadata_field_id = 25 And ";
		SQL = str1 + item_ids + str2 + str3;
		try {
			stat = con.createStatement();
			rs = stat.executeQuery(SQL);

			/* 將錯誤的資料項目，寫進ErrItemsList.txt檔案之中 */
			BufferedWriter bw = new BufferedWriter(new FileWriter(
					"ErrItemsList.txt"));

			/* 將對應的項目item_id與項目內容寫進檔案 */
			while (rs.next()) {
				bw.write(String.format("%d : %s\r\n", rs.getInt("item_id"),
						rs.getString("text_value")));
			}

			bw.flush();
			bw.close();

			/* 將旗標設置，並啟動事件，通知UI更新 */
			errItems = true;
			this.setChanged();
			this.notifyObservers();
			return true;
		} catch (Exception e) {
			log(e);
		} finally {
			Close();
		}

		return false;
	}

	/**
	 * 功能 : 利用selectItemByCommunity所取得的item id，來產生目標xml檔
	 * 
	 * @param filePath
	 *            選擇的檔案資料夾路徑
	 */
	public void createXML(String filePath) {

		try {
			if (con == null || con.isClosed()) {
				log(new Exception("Database has been close."));
				return;
			} else if (selectedItems.size() == 0) {
				log(new Exception("No item is searched."));
				return;
			}
		} catch (Exception e) {
			log(e);
			return;
		}

		System.out.println("Start Create XML..." + filePath);

		String startItemID = "0", endItemID = "0";

		String SQL = new String();
		String str1 = "Select "
				+ "metadatavalue.item_id, "
				+ "metadatavalue.metadata_field_id, "
				+ "metadatavalue.text_value, "
				+ "metadatafieldregistry.element, "
				+ "collection.name, "
				+ "community.name As Department "
				+ "From "
				+ "metadatavalue Inner Join "
				+ "metadatafieldregistry On metadatavalue.metadata_field_id = "
				+ "metadatafieldregistry.metadata_field_id Inner Join "
				+ "collection2item On metadatavalue.item_id = collection2item.item_id Inner Join "
				+ "collection On collection2item.collection_id = collection.collection_id "
				+ "Inner Join "
				+ "community2collection On collection.collection_id = "
				+ "community2collection.collection_id Inner Join "
				+ "community On community2collection.community_id = community.community_id "
				+ "Inner Join "
				+ "item On metadatavalue.item_id = item.item_id " + "Where "
				+ "metadatavalue.item_id In (";
		StringBuilder item_ids = new StringBuilder();
		for (Integer item_id : selectedItems) {
			if (item_ids.length() == 0)
				item_ids.append(item_id);
			item_ids.append(", " + item_id);
		}
		selectedItems.clear();
		item_ids.append(") And ");
		String str2 = "";
		for(String collectionName : collectionNames){
			if(!"".equals(str2)) str2 += " Or ";
			else str2 = "(";
			
			str2 += String.format("(collection.name = '%s')", collectionName);
		}
		String str3 = "metadatavalue.metadata_field_id In (1, 9, 65, 25, 10) And "
				+ str2
				+ ") And "
				+ "item.withdrawn = 0 And "
				+ "item.owning_collection <> '' "
				+ "Order By "
				+ "metadatavalue.item_id, "
				+ "metadatavalue.metadata_field_id ";
		SQL = str1 + item_ids + str3;

		try {
			stat = con.createStatement();
			rs = stat.executeQuery(SQL);

			/* 先將資料寫進暫存檔之中副檔名.tmp，為了之後將其改名 */
			BufferedWriter bw = new BufferedWriter(new FileWriter(filePath
					+ ".tmp"));
			bw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n"
					+ "<books>\r\n");

			/* 以下這段是用來取得資料的項目數量 */
			if (rs.last()) {// 將取得的資料設定至最後一個項目
				finish = 0;// 將已完成數初始化
				records = rs.getRow();// 取得項目數量
				if (records == 0) {// 同時檢查是否有資料
					throw new Exception("No item is searched.");
				}
				rs.beforeFirst();// 將取得的資料設定回首項目

				/* 啟動事件，通知UI更新 */
				this.setChanged();
				this.notifyObservers();
			}

			while (rs.next()) {

				/* 取得首項目與最後一個項目之item_id，用在最後更改檔案名稱 */
				if (startItemID.equals("0"))
					startItemID = rs.getString("item_id");
				endItemID = rs.getString("item_id");

				/*
				 * 取得各項資料，由於各項資料為一個row而共有六種資料分別為 : 系所、中文姓名、英文姓名
				 * 上傳日期、URL、標題。故需要checkItemErr方法來確保有六項，避免不可預期之錯誤。
				 */
				String department = rs.getString("text_value");
				String CreatorFirst = (rs.next()) ? rs.getString("text_value")
						: "";
				String CreatorSecond = (rs.next()) ? rs.getString("text_value")
						: "";
				@SuppressWarnings("unused")
				String date = (rs.next()) ? rs.getString("text_value") : "";
				// System.out.println(date);
				String url = (rs.next()) ? rs.getString("text_value") : "";
				String Title = (rs.next()) ? rs.getString("text_value") : "";

				/* 之將從資料庫取得的資料按照格式寫入檔案之中 */
				bw.write("<book>\r\n");
				bw.write("<ThesisID>");
				bw.write("</ThesisID>\r\n");
				bw.write("<Creator>");
				/* 以中文姓名為主，假如沒有中文姓名則填入英文姓名 */
				Pattern pattern = Pattern
						.compile("[\\p{InCJKUnifiedIdeographs}]");// 中文的Regular
																	// Expression
																	// Unicode編碼
				Matcher match = pattern.matcher(CreatorFirst);
				if (match.find()) {
					bw.write(CreatorFirst);
				} else {
					if (!CreatorSecond.equals("")) {
						bw.write(CreatorSecond);
					} else {
						bw.write(CreatorFirst);
					}
				}
				bw.write("</Creator>\r\n");
				bw.write("<Title>");
				bw.write(Title);
				bw.write("</Title>\r\n");
				bw.write("<Title.translated>");
				bw.write("</Title.translated>\r\n");
				bw.write("<Description.note.school>");
				bw.write("國立成功大學");
				bw.write("</Description.note.school>\r\n");
				bw.write("<Description.note.department>");
				bw.write(department);
				bw.write("</Description.note.department>\r\n");
				bw.write("<School.url>");
				bw.write(url);
				bw.write("</School.url>\r\n");
				bw.write("</book>\r\n");

				/* 每完成一個項目，啟動事件，通知UI更新 */
				finish++;
				this.setChanged();
				this.notifyObservers();
			}

			bw.write("</books>");
			bw.flush();
			bw.close();

			/* 檔案完成後，更改其檔名，由於item_id在資料庫之中，所以只能這麼做 */
			File f = new File(filePath + ".tmp");
			if (startItemID.equals("0")) {
				f.delete();
			} else {
				if (!f.renameTo(new File(String.format("%s(%s-%s).xml",
						filePath, startItemID, endItemID)))) {
					f.delete();
				}
			}

		} catch (Exception e) {
			log(e);
		} finally {
			Close();
		}

		/* 完成後，啟動事件，通知UI更新 */
		finish = records;
		this.setChanged();
		this.notifyObservers();
	}

	/**
	 * 功能 : 從資料庫取得系所列表，並存進communityTable
	 */
	private void getCommunities() {
		String name;

		communityTable.put(0, "所有系所");
		try {
			stat = con.createStatement();
			rs = stat.executeQuery(selectCommunitySQL);

			while (rs.next()) {
				name = rs.getString("name");
				/* 將有以下字尾的字串加進communityTable 各自有對應值(有唯一性)當作key */
				if (name.endsWith("班") || name.endsWith("系")
						|| name.endsWith("所") || name.endsWith("學程")
						|| name.endsWith("學院"))
					communityTable.put(rs.getInt("community_id"), name);
			}
		} catch (Exception e) {
			log(e);
		} finally {
			Close();
		}
	}

	/**
	 * 功能 : 關閉所有有關資料庫之物件否則在等待Timeout時，可能會有Connection poor的狀況
	 */
	private void Close() {
		try {
			if (rs != null) {
				rs.close();
				rs = null;
			}
			if (stat != null) {
				stat.close();
				stat = null;
			}
		} catch (Exception e) {
			log(e);
		}
	}

	/**
	 * 功能 : 將發生的例外寫進Log檔之中
	 * 
	 * @param e
	 *            例外實體物件
	 */
	private void log(Exception e) {
		try {
			FileWriter log;
			log = new FileWriter(logFileName, true);
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));// 將例外堆疊寫進errors字串之中(將標準輸出導向)
			log.write(getClockTime() + " : \r\n\t" + errors);// 連同時間與例外堆疊寫進檔案之中
			log.close();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		/* 啟動事件，通知UI更新 */
		this.setChanged();
		this.notifyObservers();
	}

	/**
	 * 
	 * @return 取得當日日期 格式yyyy-MM-dd
	 */
	private static String getDateTime() {
		// ==格式化
		SimpleDateFormat nowdate = new java.text.SimpleDateFormat("yyyy-MM-dd");

		// ==GMT標準時間往後加八小時
		nowdate.setTimeZone(TimeZone.getTimeZone("GMT+8"));

		// ==取得目前時間
		return nowdate.format(new java.util.Date());
	}

	/**
	 * 
	 * @return 取得當時執行時間 格式HH:mm:ss
	 */
	private static String getClockTime() {
		// ==格式化
		SimpleDateFormat nowdate = new java.text.SimpleDateFormat("HH:mm:ss");

		// ==GMT標準時間往後加八小時
		nowdate.setTimeZone(TimeZone.getTimeZone("GMT+8"));

		// ==取得目前時間
		return nowdate.format(new java.util.Date());
	}
}
