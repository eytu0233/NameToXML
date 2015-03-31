package edu.ncku.jdbc;

import java.awt.EventQueue;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.EmptyBorder;
import javax.swing.JButton;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JTextField;
import javax.swing.JLabel;

import javax.swing.JProgressBar;

import java.awt.Font;

import javax.swing.SwingConstants;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;

import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.awt.Toolkit;

public class MainFrame extends JFrame implements Observer {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JFrame frame = this;
	private JPanel contentPane;
	private JTextField txtPath;
	private JProgressBar progressBar;
	private JTextField textField_4;
	private JTextField textField_5;

	private JDBCMySQL connector;
	private JCheckBox chbNow;
	private JCheckBox chbBefore;
	private JComboBox<Integer> cbSYear;
	private JComboBox<String> cbSMonth;
	private JComboBox<String> cbSDay;
	private JComboBox<Integer> cbEYear;
	private JComboBox<String> cbEMonth;
	private JComboBox<String> cbEDay;
	private JComboBox<String> cbCommunity;
	private JButton btnStart;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MainFrame frame = new MainFrame();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public MainFrame() {
		setIconImage(Toolkit.getDefaultToolkit().getImage(
				"C:\\Users\\Administrator\\Desktop\\icon.png"));
		setTitle("NCtoXML轉檔程式");

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedLookAndFeelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		
		connector = new JDBCMySQL();
		connector.addObserver(this);

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 437, 342);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);

		txtPath = new JTextField();
		txtPath.setBounds(10, 143, 290, 26);
		contentPane.add(txtPath);
		txtPath.setColumns(10);

		JButton button = new JButton("選擇路徑");
		button.setFont(new Font("新細明體", Font.BOLD, 12));
		button.setBounds(310, 142, 95, 26);
		contentPane.add(button);

		progressBar = new JProgressBar();
		progressBar.setBounds(10, 268, 290, 26);
		contentPane.add(progressBar);

		btnStart = new JButton("開始轉換");
		btnStart.setFont(new Font("新細明體", Font.BOLD, 12));
		btnStart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				progressBar.setValue(0);//初始化進度條
				btnStart.setEnabled(false);//鎖定開始按鈕

				if (txtPath.getText().equals("")) {//使用者尚未選擇存放資料夾時，通知使用者
					JOptionPane.showMessageDialog(frame, "請選擇檔案放置的資料夾!", "錯誤",
							JOptionPane.ERROR_MESSAGE);
					btnStart.setEnabled(true);
				} else {
					Integer sy = 0, sm = 0, sd = 0, ey = 0, em = 0, ed = 0, community_id = null;
					LinkedHashMap<Integer, String> communities = connector
							.getCommunityTable();

					/* 取得UI上日期的值，當日期元件失效時，只有初始值0，表示不使用起始日期或終止日期 */
					if (cbSYear.isEnabled()) {
						sy = Integer.valueOf(cbSYear.getModel()
								.getSelectedItem().toString());
						sm = Integer.valueOf(cbSMonth.getModel()
								.getSelectedItem().toString());
						sd = Integer.valueOf(cbSDay.getModel()
								.getSelectedItem().toString());
					}

					if (cbEYear.isEnabled()) {
						ey = Integer.valueOf(cbEYear.getModel()
								.getSelectedItem().toString());
						em = Integer.valueOf(cbEMonth.getModel()
								.getSelectedItem().toString());
						ed = Integer.valueOf(cbEDay.getModel()
								.getSelectedItem().toString());
					}

					/* 避免開始時間比結束時間晚 */
					if (sy != 0
							&& ey != 0
							&& (sy * 365 + sm * 30 + sd > ey * 365 + em * 30
									+ ed)) {
						JOptionPane.showMessageDialog(frame, "日期設定錯誤!", "錯誤",
								JOptionPane.ERROR_MESSAGE);
						btnStart.setEnabled(true);
						return;
					}

					/* 利用communities的Map取得對應之系所值 */
					for (Integer i : communities.keySet()) {
						if (communities.get(i).equals(
								cbCommunity.getModel().getSelectedItem())) {
							System.out.println("community_id : " + i);
							community_id = i;
							break;
						}
					}

					/* 啟動轉換程式主類別執行續，避免阻塞UI線程 */
					Thread t = new Thread(new ConnectorThread(connector,
							txtPath.getText(), sy, sm, sd, ey, em, ed,
							community_id));
					t.start();
				}
			}
		});
		btnStart.setBounds(310, 268, 95, 26);
		contentPane.add(btnStart);

		textField_4 = new JTextField();
		textField_4.setText("查詢時間 : ");
		textField_4.setFont(new Font("新細明體", Font.BOLD, 16));
		textField_4.setEditable(false);
		textField_4.setColumns(10);
		textField_4.setBounds(10, 10, 95, 26);
		contentPane.add(textField_4);

		textField_5 = new JTextField();
		textField_5.setText("查詢系所 : ");
		textField_5.setFont(new Font("新細明體", Font.BOLD, 16));
		textField_5.setEditable(false);
		textField_5.setColumns(10);
		textField_5.setBounds(10, 107, 95, 26);
		contentPane.add(textField_5);

		cbSYear = new JComboBox<Integer>();
		cbSYear.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent arg0) {
				if (!cbEYear.getSelectedItem().equals(arg0.getItem()))
					cbEYear.setSelectedItem(arg0.getItem());
			}
		});
		cbSYear.setModel(new DefaultComboBoxModel<Integer>(getYears()));
		cbSYear.setFont(new Font("新細明體", Font.BOLD, 16));
		cbSYear.setBounds(10, 46, 67, 26);
		contentPane.add(cbSYear);

		JLabel lblNewLabel = new JLabel("~");
		lblNewLabel.setFont(new Font("新細明體", Font.BOLD, 24));
		lblNewLabel.setHorizontalAlignment(SwingConstants.CENTER);
		lblNewLabel.setBounds(194, 44, 24, 26);
		contentPane.add(lblNewLabel);

		cbSMonth = new JComboBox<String>();
		cbSMonth.setModel(new DefaultComboBoxModel<String>(new String[] { "1", "2",
				"3", "4", "5", "6", "7", "8", "9", "10", "11", "12" }));
		cbSMonth.setFont(new Font("新細明體", Font.BOLD, 16));
		cbSMonth.setBounds(87, 46, 49, 26);
		contentPane.add(cbSMonth);

		cbSDay = new JComboBox<String>();
		cbSDay.setModel(new DefaultComboBoxModel<String>(new String[] { "1", "2", "3",
				"4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14",
				"15", "16", "17", "18", "19", "20", "21", "22", "23", "24",
				"25", "26", "27", "28", "29", "30", "31" }));
		cbSDay.setFont(new Font("新細明體", Font.BOLD, 16));
		cbSDay.setBounds(146, 46, 49, 26);
		contentPane.add(cbSDay);

		cbEDay = new JComboBox<String>();
		cbEDay.setModel(new DefaultComboBoxModel<String>(new String[] { "1", "2", "3",
				"4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14",
				"15", "16", "17", "18", "19", "20", "21", "22", "23", "24",
				"25", "26", "27", "28", "29", "30", "31" }));
		cbEDay.setFont(new Font("新細明體", Font.BOLD, 16));
		cbEDay.setBounds(356, 46, 49, 26);
		contentPane.add(cbEDay);

		cbEMonth = new JComboBox<String>();
		cbEMonth.setModel(new DefaultComboBoxModel<String>(new String[] { "1", "2",
				"3", "4", "5", "6", "7", "8", "9", "10", "11", "12" }));
		cbEMonth.setFont(new Font("新細明體", Font.BOLD, 16));
		cbEMonth.setBounds(297, 46, 49, 26);
		contentPane.add(cbEMonth);

		cbEYear = new JComboBox<Integer>();
		cbEYear.setModel(new DefaultComboBoxModel<Integer>(getYears()));
		cbEYear.setFont(new Font("新細明體", Font.BOLD, 16));
		cbEYear.setBounds(220, 46, 67, 26);
		contentPane.add(cbEYear);

		cbCommunity = new JComboBox<String>();
		cbCommunity.setFont(new Font("新細明體", Font.BOLD, 16));
		cbCommunity.setBounds(115, 107, 290, 26);
		LinkedHashMap<Integer, String> communities = connector
				.getCommunityTable();
		LinkedHashSet<String> noDuplicateCommunities = new LinkedHashSet<String>();
		for (String community : communities.values()) {
			noDuplicateCommunities.add(community);
		}

		cbCommunity.setModel(new DefaultComboBoxModel<String>(noDuplicateCommunities
				.toArray(new String[] {})));
		contentPane.add(cbCommunity);

		chbBefore = new JCheckBox("之前全部");
		chbBefore.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				/* 偵測checkbox事件，並使起始日期元件失效或有效 */
				if (chbBefore.isSelected()) {
					cbSYear.setEnabled(false);
					cbSMonth.setEnabled(false);
					cbSDay.setEnabled(false);
				} else {
					cbSYear.setEnabled(true);
					cbSMonth.setEnabled(true);
					cbSDay.setEnabled(true);
				}
			}
		});
		chbBefore.setFont(new Font("新細明體", Font.BOLD, 16));
		chbBefore.setBounds(10, 78, 97, 23);
		contentPane.add(chbBefore);

		chbNow = new JCheckBox("直到最新");
		chbNow.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				/* 偵測checkbox事件，並使終止日期元件失效或有效 */
				if (chbNow.isSelected()) {
					cbEYear.setEnabled(false);
					cbEMonth.setEnabled(false);
					cbEDay.setEnabled(false);
				} else {
					cbEYear.setEnabled(true);
					cbEMonth.setEnabled(true);
					cbEDay.setEnabled(true);
				}
			}
		});
		chbNow.setFont(new Font("新細明體", Font.BOLD, 16));
		chbNow.setBounds(115, 78, 97, 23);
		contentPane.add(chbNow);
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				/* 讓使用者選擇資料夾，並取得絕對路徑 */
				JFileChooser chooser = new JFileChooser();
				chooser.setCurrentDirectory(new java.io.File("."));
				chooser.setDialogTitle("選擇資料夾");
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				chooser.setAcceptAllFileFilterUsed(false);
				if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
					txtPath.setText(chooser.getSelectedFile().getAbsolutePath()
							+ "\\");
				}
			}
		});
	}

	/**
	 * 為避免程式失去時間上的彈性，開發日期為2014年底，要是不使用此方法會造成沒有2015年的選擇項
	 * 
	 * @return 回傳從1980年至今的年份
	 */
	private Integer[] getYears() {
		Calendar calendar = Calendar.getInstance();
		int thisYear = calendar.get(Calendar.YEAR);
		LinkedList<Integer> years = new LinkedList<Integer>();

		for (Integer y = 1980; y <= thisYear; y++) {
			years.add(y);
		}
		return years.toArray(new Integer[] {});
	}

	/**
	 * 使用觀察者介面，監聽來自主程式的事件
	 */
	@Override
	public void update(Observable arg0, Object arg1) {
		// TODO Auto-generated method stub
		JDBCMySQL jm = (JDBCMySQL) arg0;//強制型別轉換
		
		/* 基本上只有四種事件1.項目錯誤事件2.無搜尋項目事件3.正常執行刷新進度條事件4.執行完成事件 */
		if (jm.isErrItems()) {
			jm.setErrItems(false);
			JOptionPane.showMessageDialog(frame,
					"發現不正確的項目，請參考ErrItemsList.txt。");
			btnStart.setEnabled(true);
			return;
		} else if (jm.getRecords() == 0 && jm.getFinish() == 0) {
			JOptionPane.showMessageDialog(frame, "沒有搜尋到任何項目。");
			btnStart.setEnabled(true);
			return;
		}

		progressBar.setMaximum(jm.getRecords());
		progressBar.setValue(jm.getFinish());

		if (progressBar.getMaximum() == progressBar.getValue()) {
			JOptionPane.showMessageDialog(frame, String.format(
					"已完成%d個項目的轉換。",
					(jm.getRecords() % 6 == 0) ? jm.getRecords() / 6 : jm
							.getRecords() / 5));
			btnStart.setEnabled(true);
		}
	}
}

/* 內部類別，實現Runnable介面 */
class ConnectorThread implements Runnable {

	private JDBCMySQL connector;
	private Integer sy, sm, sd, ey, em, ed, community_id;
	private String path;

	/**
	 * 
	 * @param connector 資料庫連結實體
	 * @param path 檔案存放目錄絕對路徑
	 * @param sy 起始年
	 * @param sm 起始月
	 * @param sd 起始日
	 * @param ey 終止年
	 * @param em 終止月
	 * @param ed 終止日
	 * @param community_id 系所值
	 */
	public ConnectorThread(JDBCMySQL connector, String path, Integer sy,
			Integer sm, Integer sd, Integer ey, Integer em, Integer ed,
			Integer community_id) {
		// TODO Auto-generated constructor stub
		this.connector = connector;
		this.path = path;
		this.sy = sy;
		this.sm = sm;
		this.sd = sd;
		this.ey = ey;
		this.em = em;
		this.ed = ed;
		this.community_id = community_id;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		connector.selectItemIDByCommunityAndDate(sy, sm, sd, ey, em, ed,
				community_id);
		if (!connector.checkItemErr()) {
			connector.createXML(String.format("%s%s%s-%s%stoXCL", path,
					(sy == 0) ? "PAST" : String.valueOf(sy), (sm == 0) ? ""
							: String.valueOf(sm),
					(ey == 0) ? "NOW" : String.valueOf(ey), (em == 0) ? ""
							: String.valueOf(em)));
		}
	}

}
