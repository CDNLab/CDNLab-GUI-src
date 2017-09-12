package gui;

import java.awt.EventQueue;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JTextField;
import domain.CDNLabConfig;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.Vector;

import javax.swing.JRadioButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JList;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;

public class CDNLabUI {

	private JFrame frmCdnlab;
	public final JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
	private JTextField txt_url;
	private JTextField txt_selectedFile;
	private JTextField txt_SeletedUsersFile;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					CDNLabUI window = new CDNLabUI();
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
							//.getCrossPlatformLookAndFeelClassName());
					window.frmCdnlab.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public CDNLabUI() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmCdnlab = new JFrame();
		frmCdnlab.setTitle("CDNLab");
		frmCdnlab.setBounds(100, 100, 458, 454);
		frmCdnlab.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		// ---------------------------------------------------------------------------------------------------------------------------
		Panel_Init panel_1 = new Panel_Init();
		panel_1.button_next.setLocation(338, 326);
		panel_1.button_next.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				CDNLabConfig.getInstance();
				if (CDNLabConfig.checkPhase1()) {
					int index = 1;
					for (int i = 0; i < tabbedPane.getTabCount(); i++) {
						tabbedPane.setEnabledAt(i, false);
					}
					tabbedPane.setSelectedIndex(index);
					tabbedPane.setEnabledAt(index, true);
				} else {
					JOptionPane.showMessageDialog(frmCdnlab,
							"Please fill the requested data completely!");
				}
			}
		});
		tabbedPane.addTab("Step 1: Initialize", panel_1);

		// ---------------------------------------------------------------------------------------------------------------------------
		Panel_Routers panel_2 = new Panel_Routers();
		final String filePath = "";
		panel_2.button_next.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				CDNLabConfig.getInstance();
				if (CDNLabConfig.checkPhase2()) {
					int index = 2;
					for (int i = 0; i < tabbedPane.getTabCount(); i++) {
						tabbedPane.setEnabledAt(i, false);
					}
					tabbedPane.setSelectedIndex(index);
					tabbedPane.setEnabledAt(index, true);
				} else {
					JOptionPane.showMessageDialog(frmCdnlab,
							"Please fill the requested data completely!");
				}
			}
		});
		panel_2.button_previous.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				int index = 0;
				for (int i = 0; i < tabbedPane.getTabCount(); i++) {
					tabbedPane.setEnabledAt(i, false);
				}
				tabbedPane.setSelectedIndex(index);
				tabbedPane.setEnabledAt(index, true);
			}
		});
		tabbedPane.addTab("Step 2: Define Routers", panel_2);
		panel_2.setLayout(null);
		// ---------------------------------------------------------------------------------------------------------------------------
		JComponent panel_3 = new JPanel();
		tabbedPane.addTab("Step 3: Import Contents", panel_3);
		panel_3.setLayout(null);

		JButton btnNext = new JButton("Next");
		btnNext.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				CDNLabConfig.getInstance();
				try {
					CDNLabConfig.generateContents(txt_selectedFile.getText(),
							txt_url.getText());
					int index = 3;
					for (int i = 0; i < tabbedPane.getTabCount(); i++) {
						tabbedPane.setEnabledAt(i, false);
					}
					tabbedPane.setSelectedIndex(index);
					tabbedPane.setEnabledAt(index, true);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					JOptionPane.showMessageDialog(frmCdnlab, e.getMessage());
				}
			}
		});
		btnNext.setBounds(338, 321, 89, 23);
		panel_3.add(btnNext);

		JButton btnPrevious = new JButton("Previous");
		btnPrevious.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				int index = 1;
				for (int i = 0; i < tabbedPane.getTabCount(); i++) {
					tabbedPane.setEnabledAt(i, false);
				}
				tabbedPane.setSelectedIndex(index);
				tabbedPane.setEnabledAt(index, true);
			}
		});
		btnPrevious.setBounds(10, 321, 89, 23);
		panel_3.add(btnPrevious);

		JLabel lblPleaseSelectAn = new JLabel(
				"Please select an Apache Access Log file to import contents from");
		lblPleaseSelectAn.setBounds(10, 11, 417, 14);
		panel_3.add(lblPleaseSelectAn);

		final JLabel lblSelectedfile = new JLabel("Selected File:");
		lblSelectedfile.setBounds(10, 70, 99, 14);
		panel_3.add(lblSelectedfile);

		JButton btnSelectFile = new JButton("Select File");
		btnSelectFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser chooser = new JFileChooser();
				chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
				FileNameExtensionFilter filter = new FileNameExtensionFilter(
						"Access Log files (*.log, *.txt)", "log", "txt");
				chooser.setFileFilter(filter);
				int returnVal = chooser.showOpenDialog(null);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					txt_selectedFile.setText(chooser.getSelectedFile()
							.getAbsolutePath());

				}
			}
		});
		btnSelectFile.setBounds(10, 36, 105, 23);
		panel_3.add(btnSelectFile);

		JLabel lblWebsiteUrl = new JLabel("Website URL:");
		lblWebsiteUrl.setBounds(10, 118, 105, 14);
		panel_3.add(lblWebsiteUrl);

		txt_url = new JTextField();
		txt_url.setBounds(119, 115, 308, 20);
		panel_3.add(txt_url);
		txt_url.setColumns(10);

		txt_selectedFile = new JTextField();
		txt_selectedFile.setEditable(false);
		txt_selectedFile.setBounds(119, 67, 308, 20);
		panel_3.add(txt_selectedFile);
		txt_selectedFile.setColumns(10);

		// ---------------------------------------------------------------------------------------------------------------------------
		JComponent panel_4 = new JPanel();
		tabbedPane.addTab("Step 4: Connect Clients", panel_4);
		panel_4.setLayout(null);

		JButton btnNext_1 = new JButton("Next");
		btnNext_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				CDNLabConfig.getInstance();
				try {
					CDNLabConfig.importClients(txt_SeletedUsersFile.getText());
					int index = 4;
					for (int i = 0; i < tabbedPane.getTabCount(); i++) {
						tabbedPane.setEnabledAt(i, false);
					}
					tabbedPane.setSelectedIndex(index);
					tabbedPane.setEnabledAt(index, true);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					JOptionPane.showMessageDialog(frmCdnlab, e.getMessage());
				}
			}
		});
		btnNext_1.setBounds(338, 320, 89, 23);
		panel_4.add(btnNext_1);

		JButton btnPrevious_1 = new JButton("Previous");
		btnPrevious_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				int index = 2;
				for (int i = 0; i < tabbedPane.getTabCount(); i++) {
					tabbedPane.setEnabledAt(i, false);
				}
				tabbedPane.setSelectedIndex(index);
				tabbedPane.setEnabledAt(index, true);
			}
		});
		btnPrevious_1.setBounds(10, 320, 89, 23);
		panel_4.add(btnPrevious_1);

		JLabel lblPleaseEnterThe = new JLabel(
				"Please select the users' CSV file which includes users' IP addresses and coordinates:");
		lblPleaseEnterThe.setBounds(10, 11, 417, 14);
		panel_4.add(lblPleaseEnterThe);

		JButton btnSelectFile_1 = new JButton("Select File");
		btnSelectFile_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser chooser = new JFileChooser();
				chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
				FileNameExtensionFilter filter = new FileNameExtensionFilter(
						"CSV user files (*.csv, *.txt)", "csv", "txt");
				chooser.setFileFilter(filter);
				int returnVal = chooser.showOpenDialog(null);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					txt_SeletedUsersFile.setText(chooser.getSelectedFile()
							.getAbsolutePath());

				}
			}
		});
		btnSelectFile_1.setBounds(10, 36, 114, 23);
		panel_4.add(btnSelectFile_1);

		JLabel label = new JLabel("Selected File:");
		label.setBounds(10, 70, 99, 14);
		panel_4.add(label);

		txt_SeletedUsersFile = new JTextField();
		txt_SeletedUsersFile.setEditable(false);
		txt_SeletedUsersFile.setColumns(10);
		txt_SeletedUsersFile.setBounds(119, 67, 308, 20);
		panel_4.add(txt_SeletedUsersFile);
		// ---------------------------------------------------------------------------------------------------------------------------
		JComponent panel_5 = new JPanel();
		tabbedPane.addTab("Step 5: Select Servers", panel_5);
		ChangeListener changeListener = new ChangeListener() {
			public void stateChanged(ChangeEvent changeEvent) {
				JTabbedPane sourceTabbedPane = (JTabbedPane) changeEvent
						.getSource();
				int index = sourceTabbedPane.getSelectedIndex();
				if (index == 4) {// if selected tab is the select server tab
					// System.out.println("Tab changed");

				}
				// System.out.println("Tab changed to: " +
				// sourceTabbedPane.getTitleAt(index));
			}
		};
		tabbedPane.addChangeListener(changeListener);
		panel_5.setLayout(null);

		final JComboBox origin_combo = new JComboBox();
		origin_combo.setModel(new DefaultComboBoxModel(new String[] {
				"asRouter_A1 [US_1]", "asRouter_A2 []",
				"asRouter_AE [UNITED ARAB EMIRATES]",
				"asRouter_AF [AFGHANISTAN]",
				"asRouter_AG [ANTIGUA AND BARBUDA]", "asRouter_AL [ALBANIA]",
				"asRouter_AM [ARMENIA]", "asRouter_AN [NETHERLANDS ANTILLES]",
				"asRouter_AO [ANGOLA]", "asRouter_AP []",
				"asRouter_AR [ARGENTINA]", "asRouter_AS [AMERICAN SAMOA]",
				"asRouter_AT [AUSTRIA]", "asRouter_AU [AUSTRALIA]",
				"asRouter_AW [ARUBA]", "asRouter_AX []",
				"asRouter_AZ [AZERBAIJAN]",
				"asRouter_BA [BOSNIA AND HERZEGOWINA]",
				"asRouter_BB [BARBADOS]", "asRouter_BD [BANGLADESH]",
				"asRouter_BE [BELGIUM]", "asRouter_BF [BURKINA FASO]",
				"asRouter_BG [BULGARIA]", "asRouter_BH [BAHRAIN]",
				"asRouter_BM [BERMUDA]", "asRouter_BN [BRUNEI DARUSSALAM]",
				"asRouter_BO [BOLIVIA]", "asRouter_BS [BAHAMAS]",
				"asRouter_BY [BELARUS]", "asRouter_BZ [BELIZE]",
				"asRouter_CA [CANADA]", "asRouter_CD [CONGO  THE DRC]",
				"asRouter_CH [SWITZERLAND]", "asRouter_CL [CHILE]",
				"asRouter_CN [CHINA]", "asRouter_CO [COLOMBIA]",
				"asRouter_CR [COSTA RICA]", "asRouter_CY [CYPRUS]",
				"asRouter_CZ [CZECH REPUBLIC]", "asRouter_DE [GERMANY]",
				"asRouter_DK [DENMARK]", "asRouter_DO [DOMINICAN REPUBLIC]",
				"asRouter_DZ [ALGERIA]", "asRouter_EE [ESTONIA]",
				"asRouter_EG [EGYPT]", "asRouter_ES [SPAIN]", "asRouter_EU []",
				"asRouter_FI [FINLAND]",
				"asRouter_FK [FALKLAND ISLANDS (MALVINAS)]",
				"asRouter_FM [MICRONESIA  FEDERATED STATES OF]",
				"asRouter_FO [FAROE ISLANDS]", "asRouter_FR [FRANCE]",
				"asRouter_GA [GABON]", "asRouter_GB [UNITED KINGDOM]",
				"asRouter_GE [GEORGIA]", "asRouter_GH [GHANA]",
				"asRouter_GI [GIBRALTAR]", "asRouter_GR [GREECE]",
				"asRouter_GT [GUATEMALA]", "asRouter_GU [GUAM]",
				"asRouter_HK [HONG KONG]", "asRouter_HN [HONDURAS]",
				"asRouter_HR [CROATIA (local name: Hrvatska)]",
				"asRouter_HU [HUNGARY]", "asRouter_ID [INDONESIA]",
				"asRouter_IE [IRELAND]", "asRouter_IL [ISRAEL]",
				"asRouter_IM []", "asRouter_IN [INDIA]", "asRouter_IQ [IRAQ]",
				"asRouter_IR [IRAN (ISLAMIC REPUBLIC OF)]",
				"asRouter_IS [ICELAND]", "asRouter_IT [ITALY]",
				"asRouter_JE []", "asRouter_JM [JAMAICA]",
				"asRouter_JO [JORDAN]", "asRouter_JP [JAPAN]",
				"asRouter_KE [KENYA]", "asRouter_KG [KYRGYZSTAN]",
				"asRouter_KH [CAMBODIA]", "asRouter_KM [COMOROS]",
				"asRouter_KR [KOREA  REPUBLIC OF]", "asRouter_KW [KUWAIT]",
				"asRouter_KY [CAYMAN ISLANDS]", "asRouter_KZ [KAZAKHSTAN]",
				"asRouter_LA [LAOS ]", "asRouter_LB [LEBANON]",
				"asRouter_LI [LIECHTENSTEIN]", "asRouter_LT [LITHUANIA]",
				"asRouter_LU [LUXEMBOURG]", "asRouter_LV [LATVIA]",
				"asRouter_MA [MOROCCO]", "asRouter_MC [MONACO]",
				"asRouter_MD [MOLDOVA  REPUBLIC OF]",
				"asRouter_ME [MONTENEGRO]", "asRouter_MG [MADAGASCAR]",
				"asRouter_MH [MARSHALL ISLANDS]", "asRouter_MK [MACEDONIA]",
				"asRouter_MQ [MARTINIQUE]", "asRouter_MT [MALTA]",
				"asRouter_MU [MAURITIUS]", "asRouter_MX [MEXICO]",
				"asRouter_MY [MALAYSIA]", "asRouter_MZ [MOZAMBIQUE]",
				"asRouter_NG [NIGERIA]", "asRouter_NI [NICARAGUA]",
				"asRouter_NL [NETHERLANDS]", "asRouter_NO [NORWAY]",
				"asRouter_NZ [NEW ZEALAND]", "asRouter_OM [OMAN]",
				"asRouter_PA [PANAMA]", "asRouter_PF [FRENCH POLYNESIA]",
				"asRouter_PH [PHILIPPINES]", "asRouter_PK [PAKISTAN]",
				"asRouter_PL [POLAND]", "asRouter_PR [PUERTO RICO]",
				"asRouter_PT [PORTUGAL]", "asRouter_PY [PARAGUAY]",
				"asRouter_RE [REUNION]", "asRouter_RO [ROMANIA]",
				"asRouter_RS [SERBIA]", "asRouter_RU [RUSSIAN FEDERATION]",
				"asRouter_RW [RWANDA]", "asRouter_SA [SAUDI ARABIA]",
				"asRouter_SC [SEYCHELLES]", "asRouter_SD [SUDAN]",
				"asRouter_SE [SWEDEN]", "asRouter_SG [SINGAPORE]",
				"asRouter_SI [SLOVENIA]",
				"asRouter_SK [SLOVAKIA (Slovak Republic)]",
				"asRouter_SL [SIERRA LEONE]", "asRouter_SM [SAN MARINO]",
				"asRouter_SO [SOMALIA]", "asRouter_SV [EL SALVADOR]",
				"asRouter_TH [THAILAND]", "asRouter_TJ [TAJIKISTAN]",
				"asRouter_TN [TUNISIA]", "asRouter_TR [TURKEY]",
				"asRouter_TT [TRINIDAD AND TOBAGO]",
				"asRouter_TW [TAIWAN  PROVINCE OF CHINA]",
				"asRouter_TZ [TANZANIA  UNITED REPUBLIC OF]",
				"asRouter_UA [UKRAINE]", "asRouter_UG [UGANDA]",
				"asRouter_US [UNITED STATES]", "asRouter_UZ [UZBEKISTAN]",
				"asRouter_VE [VENEZUELA]",
				"asRouter_VG [VIRGIN ISLANDS (BRITISH)]",
				"asRouter_VI [VIRGIN ISLANDS (U.S.)]",
				"asRouter_VN [VIET NAM]", "asRouter_WS [SAMOA]",
				"asRouter_ZA [SOUTH AFRICA]", "asRouter_ZM [ZAMBIA]" }));
		origin_combo.setBounds(120, 11, 294, 23);
		panel_5.add(origin_combo);

		final JComboBox surrogate_combo = new JComboBox();
		surrogate_combo.setModel(new DefaultComboBoxModel(new String[] {
				"asRouter_A1 [US_1]", "asRouter_A2 []",
				"asRouter_AE [UNITED ARAB EMIRATES]",
				"asRouter_AF [AFGHANISTAN]",
				"asRouter_AG [ANTIGUA AND BARBUDA]", "asRouter_AL [ALBANIA]",
				"asRouter_AM [ARMENIA]", "asRouter_AN [NETHERLANDS ANTILLES]",
				"asRouter_AO [ANGOLA]", "asRouter_AP []",
				"asRouter_AR [ARGENTINA]", "asRouter_AS [AMERICAN SAMOA]",
				"asRouter_AT [AUSTRIA]", "asRouter_AU [AUSTRALIA]",
				"asRouter_AW [ARUBA]", "asRouter_AX []",
				"asRouter_AZ [AZERBAIJAN]",
				"asRouter_BA [BOSNIA AND HERZEGOWINA]",
				"asRouter_BB [BARBADOS]", "asRouter_BD [BANGLADESH]",
				"asRouter_BE [BELGIUM]", "asRouter_BF [BURKINA FASO]",
				"asRouter_BG [BULGARIA]", "asRouter_BH [BAHRAIN]",
				"asRouter_BM [BERMUDA]", "asRouter_BN [BRUNEI DARUSSALAM]",
				"asRouter_BO [BOLIVIA]", "asRouter_BS [BAHAMAS]",
				"asRouter_BY [BELARUS]", "asRouter_BZ [BELIZE]",
				"asRouter_CA [CANADA]", "asRouter_CD [CONGO  THE DRC]",
				"asRouter_CH [SWITZERLAND]", "asRouter_CL [CHILE]",
				"asRouter_CN [CHINA]", "asRouter_CO [COLOMBIA]",
				"asRouter_CR [COSTA RICA]", "asRouter_CY [CYPRUS]",
				"asRouter_CZ [CZECH REPUBLIC]", "asRouter_DE [GERMANY]",
				"asRouter_DK [DENMARK]", "asRouter_DO [DOMINICAN REPUBLIC]",
				"asRouter_DZ [ALGERIA]", "asRouter_EE [ESTONIA]",
				"asRouter_EG [EGYPT]", "asRouter_ES [SPAIN]", "asRouter_EU []",
				"asRouter_FI [FINLAND]",
				"asRouter_FK [FALKLAND ISLANDS (MALVINAS)]",
				"asRouter_FM [MICRONESIA  FEDERATED STATES OF]",
				"asRouter_FO [FAROE ISLANDS]", "asRouter_FR [FRANCE]",
				"asRouter_GA [GABON]", "asRouter_GB [UNITED KINGDOM]",
				"asRouter_GE [GEORGIA]", "asRouter_GH [GHANA]",
				"asRouter_GI [GIBRALTAR]", "asRouter_GR [GREECE]",
				"asRouter_GT [GUATEMALA]", "asRouter_GU [GUAM]",
				"asRouter_HK [HONG KONG]", "asRouter_HN [HONDURAS]",
				"asRouter_HR [CROATIA (local name: Hrvatska)]",
				"asRouter_HU [HUNGARY]", "asRouter_ID [INDONESIA]",
				"asRouter_IE [IRELAND]", "asRouter_IL [ISRAEL]",
				"asRouter_IM []", "asRouter_IN [INDIA]", "asRouter_IQ [IRAQ]",
				"asRouter_IR [IRAN (ISLAMIC REPUBLIC OF)]",
				"asRouter_IS [ICELAND]", "asRouter_IT [ITALY]",
				"asRouter_JE []", "asRouter_JM [JAMAICA]",
				"asRouter_JO [JORDAN]", "asRouter_JP [JAPAN]",
				"asRouter_KE [KENYA]", "asRouter_KG [KYRGYZSTAN]",
				"asRouter_KH [CAMBODIA]", "asRouter_KM [COMOROS]",
				"asRouter_KR [KOREA  REPUBLIC OF]", "asRouter_KW [KUWAIT]",
				"asRouter_KY [CAYMAN ISLANDS]", "asRouter_KZ [KAZAKHSTAN]",
				"asRouter_LA [LAOS ]", "asRouter_LB [LEBANON]",
				"asRouter_LI [LIECHTENSTEIN]", "asRouter_LT [LITHUANIA]",
				"asRouter_LU [LUXEMBOURG]", "asRouter_LV [LATVIA]",
				"asRouter_MA [MOROCCO]", "asRouter_MC [MONACO]",
				"asRouter_MD [MOLDOVA  REPUBLIC OF]",
				"asRouter_ME [MONTENEGRO]", "asRouter_MG [MADAGASCAR]",
				"asRouter_MH [MARSHALL ISLANDS]", "asRouter_MK [MACEDONIA]",
				"asRouter_MQ [MARTINIQUE]", "asRouter_MT [MALTA]",
				"asRouter_MU [MAURITIUS]", "asRouter_MX [MEXICO]",
				"asRouter_MY [MALAYSIA]", "asRouter_MZ [MOZAMBIQUE]",
				"asRouter_NG [NIGERIA]", "asRouter_NI [NICARAGUA]",
				"asRouter_NL [NETHERLANDS]", "asRouter_NO [NORWAY]",
				"asRouter_NZ [NEW ZEALAND]", "asRouter_OM [OMAN]",
				"asRouter_PA [PANAMA]", "asRouter_PF [FRENCH POLYNESIA]",
				"asRouter_PH [PHILIPPINES]", "asRouter_PK [PAKISTAN]",
				"asRouter_PL [POLAND]", "asRouter_PR [PUERTO RICO]",
				"asRouter_PT [PORTUGAL]", "asRouter_PY [PARAGUAY]",
				"asRouter_RE [REUNION]", "asRouter_RO [ROMANIA]",
				"asRouter_RS [SERBIA]", "asRouter_RU [RUSSIAN FEDERATION]",
				"asRouter_RW [RWANDA]", "asRouter_SA [SAUDI ARABIA]",
				"asRouter_SC [SEYCHELLES]", "asRouter_SD [SUDAN]",
				"asRouter_SE [SWEDEN]", "asRouter_SG [SINGAPORE]",
				"asRouter_SI [SLOVENIA]",
				"asRouter_SK [SLOVAKIA (Slovak Republic)]",
				"asRouter_SL [SIERRA LEONE]", "asRouter_SM [SAN MARINO]",
				"asRouter_SO [SOMALIA]", "asRouter_SV [EL SALVADOR]",
				"asRouter_TH [THAILAND]", "asRouter_TJ [TAJIKISTAN]",
				"asRouter_TN [TUNISIA]", "asRouter_TR [TURKEY]",
				"asRouter_TT [TRINIDAD AND TOBAGO]",
				"asRouter_TW [TAIWAN  PROVINCE OF CHINA]",
				"asRouter_TZ [TANZANIA  UNITED REPUBLIC OF]",
				"asRouter_UA [UKRAINE]", "asRouter_UG [UGANDA]",
				"asRouter_US [UNITED STATES]", "asRouter_UZ [UZBEKISTAN]",
				"asRouter_VE [VENEZUELA]",
				"asRouter_VG [VIRGIN ISLANDS (BRITISH)]",
				"asRouter_VI [VIRGIN ISLANDS (U.S.)]",
				"asRouter_VN [VIET NAM]", "asRouter_WS [SAMOA]",
				"asRouter_ZA [SOUTH AFRICA]", "asRouter_ZM [ZAMBIA]" }));
		surrogate_combo.setBounds(120, 46, 294, 23);
		panel_5.add(surrogate_combo);

		final Vector<String> surrogates_vector = new Vector<String>();
		final JList surrogate_list = new JList();
		surrogate_list.setBounds(10, 90, 266, 202);
		panel_5.add(surrogate_list);

		JButton button_1 = new JButton("Next");
		button_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				CDNLabConfig.getInstance();
				try {
					CDNLabConfig.createServers(origin_combo.getSelectedItem()
							.toString(), surrogates_vector);
					int index = 5;
					for (int i = 0; i < tabbedPane.getTabCount(); i++) {
						tabbedPane.setEnabledAt(i, false);
					}
					tabbedPane.setSelectedIndex(index);
					tabbedPane.setEnabledAt(index, true);
				} catch (Exception e) {
					JOptionPane.showMessageDialog(frmCdnlab, e.getMessage());
				}
			}
		});
		button_1.setBounds(338, 316, 89, 23);
		panel_5.add(button_1);

		JButton button = new JButton("Previous");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				int index = 3;
				for (int i = 0; i < tabbedPane.getTabCount(); i++) {
					tabbedPane.setEnabledAt(i, false);
				}
				tabbedPane.setSelectedIndex(index);
				tabbedPane.setEnabledAt(index, true);
			}
		});
		button.setBounds(10, 316, 89, 23);
		panel_5.add(button);

		JLabel lblOriginServer = new JLabel("Origin Server");
		lblOriginServer.setBounds(10, 14, 100, 14);
		panel_5.add(lblOriginServer);

		JLabel lblSurrogateServer = new JLabel("Surrogate Server");
		lblSurrogateServer.setBounds(10, 49, 100, 14);
		panel_5.add(lblSurrogateServer);

		JButton button_AddToList = new JButton("Add To List");
		button_AddToList.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				// System.out.println(surrogate_combo.getSelectedItem());
				surrogates_vector.add(surrogate_combo.getSelectedItem()
						.toString());
				surrogate_list.setListData(surrogates_vector);
			}
		});
		button_AddToList.setBounds(300, 87, 114, 23);
		panel_5.add(button_AddToList);

		JButton button_clearList = new JButton("Clear List");
		button_clearList.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				surrogates_vector.removeAllElements();
				surrogate_list.setListData(surrogates_vector);
			}
		});
		button_clearList.setBounds(300, 269, 114, 23);
		panel_5.add(button_clearList);

		JButton button_Removeitem = new JButton("Remove Item");
		button_Removeitem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				surrogates_vector.remove(surrogate_list.getSelectedIndex());
				surrogate_list.setListData(surrogates_vector);
			}
		});
		button_Removeitem.setBounds(300, 233, 114, 23);
		panel_5.add(button_Removeitem);
		// ---------------------------------------------------------------------------------------------------------------------------
		JComponent panel_6 = new JPanel();
		tabbedPane.addTab("Step 6: Generate Simulation files", panel_6);
		panel_6.setLayout(null);

		JButton btnFinish = new JButton("Finish");
		btnFinish.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				CDNLabConfig.getInstance();
				String outputPath;
				try {
					outputPath = CDNLabConfig.generateSimulationFiles();
					JOptionPane
					.showMessageDialog(
							frmCdnlab,
							"Finished! Please copy the follwing folder under the INET Framework's src directory, in the OMNET++ environment: \n"
									+ outputPath);
				} catch (IOException e) {
					JOptionPane.showMessageDialog(null,e.getMessage(),"Generate final sim files",JOptionPane.ERROR_MESSAGE);
					e.printStackTrace();
				}
			}
		});
		btnFinish.setBounds(338, 313, 89, 23);
		panel_6.add(btnFinish);

		JButton button_2 = new JButton("Previous");
		button_2.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				int index = 4;
				for (int i = 0; i < tabbedPane.getTabCount(); i++) {
					tabbedPane.setEnabledAt(i, false);
				}
				tabbedPane.setSelectedIndex(index);
				tabbedPane.setEnabledAt(index, true);
			}
		});
		button_2.setBounds(10, 313, 89, 23);
		panel_6.add(button_2);

		JLabel lblPleasePushThe = new JLabel(
				"<html> Please push the finish button to generate the simulation file ...</html>");
		lblPleasePushThe.setBounds(10, 11, 417, 193);
		panel_6.add(lblPleasePushThe);

		frmCdnlab.getContentPane().add(tabbedPane, BorderLayout.CENTER);
		for (int i = 1; i < tabbedPane.getTabCount(); i++) {
			// tabbedPane.setEnabledAt(i, false);
		}
	}
}

class Panel_Init extends JPanel {
	private JTextField txt_connectionString;
	private JTextField txt_username;
	private JTextField txt_password;
	public JButton button_next = new JButton("Next");

	public Panel_Init() {
		init();
	}

	public void init() {
		setLayout(null);

		final JLabel label_selectedDir = new JLabel("Working directory: ");
		label_selectedDir.setBounds(10, 45, 414, 23);
		add(label_selectedDir);

		JButton btnSelectWorking = new JButton("Select working directory");
		btnSelectWorking.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {

				JFileChooser chooser = new JFileChooser();
				chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
				// FileNameExtensionFilter filter = new FileNameExtensionFilter(
				// "JPG & GIF Images", "jpg", "gif");
				// chooser.setFileFilter(filter);
				int returnVal = chooser.showOpenDialog(null);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					CDNLabConfig.getInstance().setWorkingDir(
							chooser.getSelectedFile().getAbsolutePath());
					label_selectedDir.setText("Working directory: "
							+ chooser.getSelectedFile().getAbsolutePath());
				}
			}
		});
		btnSelectWorking.setBounds(10, 11, 178, 23);
		add(btnSelectWorking);

		JLabel label_1 = new JLabel("Please select a directory ...");
		label_1.setBounds(210, 15, 214, 14);
		add(label_1);

		txt_connectionString = new JTextField();
		txt_connectionString.setColumns(10);
		txt_connectionString.setBounds(10, 137, 414, 20);
		txt_connectionString.setText("jdbc:mysql://localhost:3306/asn");
		add(txt_connectionString);

		JLabel label_2 = new JLabel("Enter the mysql ConnectionString:");
		label_2.setBounds(10, 112, 199, 14);
		add(label_2);

		JButton btnTest = new JButton("Test");
		btnTest.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (CDNLabConfig.testDBConnection(
						txt_connectionString.getText(), txt_username.getText(),
						txt_password.getText()) == true) {
					JOptionPane.showMessageDialog(null,
							"Mysql connection is OK!", "Mysql Connection",
							JOptionPane.INFORMATION_MESSAGE);
					CDNLabConfig.getInstance().setConnectionString(
							txt_connectionString.getText());
					CDNLabConfig.getInstance().setDbUsername(
							txt_username.getText());
					CDNLabConfig.getInstance().setDbPassword(
							txt_password.getText());
				} else {
					JOptionPane.showMessageDialog(null,
							"Error while connecting to mysql!",
							"Mysql COnnection", JOptionPane.ERROR_MESSAGE);
					CDNLabConfig.getInstance().setConnectionString("");
					CDNLabConfig.getInstance().setDbUsername("");
					CDNLabConfig.getInstance().setDbPassword("");
				}

			}
		});
		btnTest.setBounds(356, 167, 68, 23);
		add(btnTest);

		txt_username = new JTextField();
		txt_username.setColumns(10);
		txt_username.setBounds(78, 168, 86, 20);
		add(txt_username);

		JLabel label_3 = new JLabel("Username:");
		label_3.setBounds(10, 171, 58, 14);
		add(label_3);

		JLabel label_4 = new JLabel("Password:");
		label_4.setBounds(177, 171, 68, 14);
		add(label_4);

		txt_password = new JTextField();
		txt_password.setColumns(10);
		txt_password.setBounds(239, 168, 86, 20);
		add(txt_password);

		button_next.setBounds(335, 228, 89, 23);
		add(button_next);
	}
}

class Panel_Routers extends JPanel {
	JButton button_next = new JButton("Next");
	JButton button_previous = new JButton("Previous");

	public Panel_Routers() {
		init();
	}

	public void init() {
		setLayout(null);

		JRadioButton rdbtnCountryWideRouters = new JRadioButton(
				"Country Wide Routers (Light)");
		rdbtnCountryWideRouters.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				CDNLabConfig.getInstance().setRouterSet(
						"Country Wide Routers (Light)");
			}
		});
		rdbtnCountryWideRouters.setBounds(10, 41, 195, 23);
		add(rdbtnCountryWideRouters);

		JRadioButton rdbtnCountryWideRouters_1 = new JRadioButton(
				"Country Wide Routers (Complex)");
		rdbtnCountryWideRouters_1.setBounds(10, 107, 219, 23);
		add(rdbtnCountryWideRouters_1);

		JRadioButton rdbtnAsWideRouters = new JRadioButton(
				"AS wide routers (Light)");
		rdbtnAsWideRouters.setBounds(10, 176, 195, 15);
		add(rdbtnAsWideRouters);

		JRadioButton rdbtnAsWideRouters_1 = new JRadioButton(
				"AS wide routers (Complex)");
		rdbtnAsWideRouters_1.setBounds(10, 239, 227, 23);
		add(rdbtnAsWideRouters_1);

		JLabel lblPleaseSelectThe = new JLabel(
				"Please select the option which fulfills your needs in forming the Internet insfrastructure");
		lblPleaseSelectThe.setBounds(10, 11, 417, 23);
		add(lblPleaseSelectThe);

		JLabel lblEachCountryIs = new JLabel(
				"<html> Each country is assumed as a router and it will be connected to the other routers by a limited number of connections. </html>");
		lblEachCountryIs.setBounds(20, 60, 407, 36);
		add(lblEachCountryIs);

		JLabel lblEachCountryIs_1 = new JLabel(
				"<html> Each country is assumed as a router and it will be connected any other country which owns an AS Router connecting to this country. </html>");
		lblEachCountryIs_1.setBounds(20, 126, 407, 36);
		add(lblEachCountryIs_1);

		JLabel lblEachAsIs = new JLabel(
				"<html> Each AS is assumed as a routerbut the number of connections is limited </html>");
		lblEachAsIs.setBounds(20, 198, 407, 26);
		add(lblEachAsIs);

		JLabel lblEachAsIs_1 = new JLabel(
				"<html> Each AS is assumed as a router and it will be connected to other AS Routers as it is in real Internet infrastructure</html>");
		lblEachAsIs_1.setBounds(20, 258, 407, 38);
		add(lblEachAsIs_1);

		button_next.setBounds(338, 316, 89, 23);
		add(button_next);

		button_previous.setBounds(10, 316, 89, 23);
		add(button_previous);

		// Group the radio buttons.
		ButtonGroup group = new ButtonGroup();
		group.add(rdbtnCountryWideRouters);
		group.add(rdbtnCountryWideRouters_1);
		group.add(rdbtnAsWideRouters);
		group.add(rdbtnAsWideRouters_1);
	}
}

class Panel_Contents extends JPanel {
	JButton button_next = new JButton("Next");
	JButton button_previous = new JButton("Previous");

	public Panel_Contents() {
		init();
	}

	public void init() {

	}
}