package ui;

import model.Skill;
import model.Student;
import model.ExchangeRequest;
import model.Notification;
import model.Rating;
import model.LearningRoadmap;
import model.RoadmapMilestone;
import model.Session;
import model.LearningGroup;
import model.GroupLearningSession;
import service.ExchangeRequestManager;
import service.LearningRoadmapManager;
import service.NotificationManager;
import service.PortfolioManager;
import service.RewardManager;
import service.GroupLearningManager;
import service.RatingManager;
import service.SkillMatcher;
import service.SessionManager;
import service.StudentManager;
import storage.FileManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.Icon;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.DefaultListCellRenderer;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class MainFrame extends JFrame {
    private static final Color APP_BACKGROUND = new Color(0xF6F6FC);
    private static final Color SIDEBAR_BACKGROUND = Color.WHITE;
    private static final Color CARD_BACKGROUND = Color.WHITE;
    private static final Color PRIMARY_PURPLE = new Color(0x6846E8);
    private static final Color SELECTED_NAV_BACKGROUND = new Color(0xF0ECFF);
    private static final Color SOFT_BLUE = new Color(0xEFF6FF);
    private static final Color SOFT_MINT = new Color(0xEFF9F4);
    private static final Color SOFT_PEACH = new Color(0xFFF5EC);
    private static final Color SOFT_LAVENDER = new Color(0xF5F2FF);
    private static final Color ACCENT_BLUE = new Color(0x4B83E6);
    private static final Color ACCENT_MINT = new Color(0x3B9B78);
    private static final Color ACCENT_PEACH = new Color(0xD98248);
    private static final Color PRIMARY_TEXT = new Color(0x202033);
    private static final Color SECONDARY_TEXT = new Color(0x55546A);
    private static final Color MUTED_TEXT = new Color(0x74738A);
    private static final Color LIGHT_BORDER = new Color(0xC9C7D8);
    private static final Font UI_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final String OVERVIEW_KEY = "Overview";

    private final StudentManager studentManager = new StudentManager();
    private final SkillMatcher skillMatcher = new SkillMatcher();
    private final ExchangeRequestManager exchangeRequestManager =
            new ExchangeRequestManager(studentManager);
    private final SessionManager sessionManager = new SessionManager(studentManager);
    private final NotificationManager notificationManager = new NotificationManager(studentManager);
    private final GroupLearningManager groupLearningManager =
            new GroupLearningManager(studentManager, notificationManager);
    private final RatingManager ratingManager = new RatingManager(studentManager, sessionManager);
    private final PortfolioManager portfolioManager =
            new PortfolioManager(studentManager, sessionManager, ratingManager);
    private final LearningRoadmapManager learningRoadmapManager =
            new LearningRoadmapManager(studentManager);
    private final RewardManager rewardManager =
            new RewardManager(studentManager, sessionManager, learningRoadmapManager);
    private final FileManager fileManager = new FileManager();
    private final JTextField idField = new JTextField(20);
    private final JTextField nameField = new JTextField(20);
    private final JTextField emailField = new JTextField(20);
    private final JTextField departmentField = new JTextField(20);
    private final JTextField yearField = new JTextField(20);
    private final JLabel profileStatusLabel =
            new JLabel("Register a student or select a profile to update or delete.");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Student ID", "Name", "Email", "Department", "Year"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable studentTable = new JTable(tableModel);
    private final JTextField skillStudentIdField = new JTextField(12);
    private final JTextField skillNameField = new JTextField(16);
    private final JTextField categoryField = new JTextField(14);
    private final JComboBox<String> skillTypeCombo =
            new JComboBox<>(new String[]{"Can teach", "Wants to learn"});
    private final JLabel skillStatusLabel =
            new JLabel("Add skills to a student profile, search offers, or find matches.");
    private final DefaultTableModel skillTableModel = new DefaultTableModel(
            new String[]{"Student ID", "Name", "Email", "Department", "Year",
                    "Can Teach", "Wants to Learn", "Matched Skills", "Match %"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable skillTable = new JTable(skillTableModel);
    private final JTextField requesterIdField = new JTextField(14);
    private final JTextField recipientIdField = new JTextField(14);
    private final JTextField offeredSkillField = new JTextField(14);
    private final JTextField requestedSkillField = new JTextField(14);
    private final JTextField respondingRecipientIdField = new JTextField(14);
    private final JLabel exchangeStatusLabel =
            new JLabel("Send an exchange request or select a pending request to respond.");
    private final DefaultTableModel requestTableModel = new DefaultTableModel(
            new String[]{"Request ID", "Requester ID", "Requester", "Recipient ID",
                    "Recipient", "Offered Skill", "Requested Skill", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable requestTable = new JTable(requestTableModel);
    private final JComboBox<String> sessionStatusFilter = new JComboBox<>(
            new String[]{"All Sessions", "Scheduled", "Completed", "Cancelled"});
    private final JTextField sessionSearchField = new JTextField(22);
    private final DefaultTableModel sessionTableModel = new DefaultTableModel(
            new String[]{"Session ID", "Teacher Name", "Learner Name", "Skill Name",
                    "Session Date", "Session Time", "Duration", "Session Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable sessionTable = new JTable(sessionTableModel);
    private final JComboBox<StudentChoice> sessionTeacherCombo = new JComboBox<>();
    private final JComboBox<StudentChoice> sessionLearnerCombo = new JComboBox<>();
    private final JComboBox<Skill> sessionSkillCombo = new JComboBox<>();
    private final JTextField sessionDateField = new JTextField(12);
    private final JTextField sessionTimeField = new JTextField(8);
    private final JSpinner sessionDurationSpinner = new JSpinner(
            new SpinnerNumberModel(60, 1, 1440, 15));
    private final JLabel sessionStatusLabel =
            new JLabel("Schedule a session or select a session to view and manage it.");
    private final JComboBox<NotificationRecipient> notificationRecipientCombo = new JComboBox<>();
    private final JComboBox<String> notificationStatusFilter = new JComboBox<>(
            new String[]{"All Notifications", "Unread Notifications", "Read Notifications"});
    private final JTextField notificationSearchField = new JTextField(22);
    private final JLabel notificationUnreadLabel = new JLabel("Unread notifications: 0");
    private final JLabel notificationStatusLabel = new JLabel("Select a student to view notifications.");
    private final DefaultTableModel notificationTableModel = new DefaultTableModel(
            new String[]{"Notification ID", "Recipient / Student Name", "Notification Type",
                    "Message", "Date and Time", "Read Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable notificationTable = new JTable(notificationTableModel);
    private final JComboBox<StudentChoice> ratingStudentCombo = new JComboBox<>();
    private final JComboBox<CompletedSessionChoice> ratingSessionCombo = new JComboBox<>();
    private final JSpinner ratingScoreSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 5, 1));
    private final JTextArea ratingFeedbackArea = new JTextArea(4, 36);
    private final JLabel ratingAverageLabel = new JLabel("Average rating: No ratings yet");
    private final JLabel ratingStatusLabel = new JLabel("Select a student and completed session to leave feedback.");
    private final DefaultTableModel receivedRatingTableModel = new DefaultTableModel(
            new String[]{"Session ID", "Skill", "Reviewer", "Stars", "Feedback", "Date"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable receivedRatingTable = new JTable(receivedRatingTableModel);
    private final JComboBox<StudentChoice> portfolioStudentCombo = new JComboBox<>();
    private final JLabel portfolioNameLabel = new JLabel("Select a student");
    private final JLabel portfolioProfileLabel = new JLabel("Profile information");
    private final JLabel portfolioGroupsLabel = new JLabel("Learning groups: None");
    private final JLabel portfolioAverageLabel = new JLabel("Average rating: No ratings yet");
    private final DefaultTableModel portfolioSkillTableModel = new DefaultTableModel(
            new String[]{"Skills Offered", "Skills the Student Wants to Learn"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable portfolioSkillTable = new JTable(portfolioSkillTableModel);
    private final DefaultTableModel portfolioSessionTableModel = new DefaultTableModel(
            new String[]{"Session ID", "Role", "Other Participant", "Skill", "Date", "Time", "Duration"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable portfolioSessionTable = new JTable(portfolioSessionTableModel);
    private final DefaultTableModel portfolioRatingTableModel = new DefaultTableModel(
            new String[]{"Session ID", "Skill", "Reviewer", "Stars", "Feedback", "Date"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable portfolioRatingTable = new JTable(portfolioRatingTableModel);
    private final DefaultTableModel portfolioAchievementTableModel = new DefaultTableModel(
            new String[]{"Achievement Badge", "Description", "Milestone", "Date Earned"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable portfolioAchievementTable = new JTable(portfolioAchievementTableModel);
    private final JLabel portfolioStatusLabel =
            new JLabel("Select a student to view their completed sessions, ratings, and achievements.");
    private final JLabel portfolioRewardsLabel = new JLabel("Reward points: 0");
    private final JComboBox<StudentChoice> rewardStudentCombo = new JComboBox<>();
    private final JLabel rewardPointsLabel = new JLabel("Total points: 0");
    private final JProgressBar rewardSessionProgressBar = new JProgressBar();
    private final JProgressBar rewardRoadmapProgressBar = new JProgressBar(0, 100);
    private final DefaultTableModel earnedBadgeTableModel = new DefaultTableModel(
            new String[]{"Badge", "Achievement", "Date Earned"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable earnedBadgeTable = new JTable(earnedBadgeTableModel);
    private final DefaultTableModel lockedBadgeTableModel = new DefaultTableModel(
            new String[]{"Badge", "Unlock Requirement", "Progress"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable lockedBadgeTable = new JTable(lockedBadgeTableModel);
    private final DefaultTableModel rewardHistoryTableModel = new DefaultTableModel(
            new String[]{"Transaction ID", "Student", "Activity", "Points", "Date and Time"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable rewardHistoryTable = new JTable(rewardHistoryTableModel);
    private final DefaultTableModel leaderboardTableModel = new DefaultTableModel(
            new String[]{"Rank", "Student", "Total Points", "Completed Sessions",
                    "Completed Milestones", "Badges"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable leaderboardTable = new JTable(leaderboardTableModel);
    private final JLabel rewardStatusLabel = new JLabel("Select a student to view rewards.");
    private final JComboBox<StudentChoice> groupActingStudentCombo = new JComboBox<>();
    private final JTextField groupNameField = new JTextField(15);
    private final JTextField groupTopicField = new JTextField(15);
    private final JTextArea groupDescriptionArea = new JTextArea(2, 22);
    private final JSpinner groupCapacitySpinner = new JSpinner(new SpinnerNumberModel(8, 1, 500, 1));
    private final JTextField groupNameSearchField = new JTextField(14);
    private final JTextField groupSkillSearchField = new JTextField(14);
    private final JComboBox<String> groupStatusFilter = new JComboBox<>(new String[]{"All", "Active", "Closed"});
    private final JComboBox<String> groupSessionFilter =
            new JComboBox<>(new String[]{"All Sessions", "Upcoming", "Completed", "Cancelled"});
    private final DefaultTableModel learningGroupTableModel = new DefaultTableModel(
            new String[]{"Group ID", "Group Name", "Skill / Topic", "Owner", "Members", "Seats", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable learningGroupTable = new JTable(learningGroupTableModel);
    private final DefaultTableModel myGroupsTableModel = new DefaultTableModel(
            new String[]{"Group ID", "Group Name", "Skill / Topic", "Members", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable myGroupsTable = new JTable(myGroupsTableModel);
    private final JLabel groupDetailsLabel = new JLabel("Select a learning group to view its details.");
    private final DefaultTableModel groupMemberTableModel = new DefaultTableModel(
            new String[]{"Student ID", "Student Name", "Role"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable groupMemberTable = new JTable(groupMemberTableModel);
    private final DefaultTableModel groupSessionTableModel = new DefaultTableModel(
            new String[]{"Session ID", "Title", "Date", "Time", "Duration", "Organizer", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable groupSessionTable = new JTable(groupSessionTableModel);
    private final JTextField groupSessionTitleField = new JTextField(15);
    private final JTextArea groupSessionDescriptionArea = new JTextArea(2, 22);
    private final JTextField groupSessionDateField = new JTextField(10);
    private final JTextField groupSessionTimeField = new JTextField(6);
    private final JSpinner groupSessionDurationSpinner =
            new JSpinner(new SpinnerNumberModel(60, 1, 1440, 15));
    private final JLabel groupLearningStatusLabel = new JLabel("Create a group or select one to get started.");
    private Integer selectedLearningGroupId;
    private final JComboBox<RoadmapStudentChoice> roadmapStudentFilter = new JComboBox<>();
    private final JComboBox<String> roadmapStatusFilter = new JComboBox<>(
            new String[]{"All", "Not Started", "In Progress", "Completed"});
    private final DefaultTableModel roadmapTableModel = new DefaultTableModel(
            new String[]{"Roadmap ID", "Student", "Title", "Target Skill", "Created",
                    "Target Date", "Progress", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable roadmapTable = new JTable(roadmapTableModel);
    private final JProgressBar roadmapProgressBar = new JProgressBar(0, 100);
    private final JLabel roadmapDetailsLabel = new JLabel("Select a roadmap to view its milestones.");
    private final DefaultTableModel roadmapMilestoneTableModel = new DefaultTableModel(
            new String[]{"Milestone ID", "Title", "Description", "Due Date", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable roadmapMilestoneTable = new JTable(roadmapMilestoneTableModel);
    private final JLabel roadmapStatusLabel =
            new JLabel("Create a roadmap to track learning progress.");
    private final JLabel totalStudentsValue = createDashboardValue();
    private final JLabel availableSkillsValue = createDashboardValue();
    private final JLabel pendingRequestsValue = createDashboardValue();
    private final JLabel acceptedExchangesValue = createDashboardValue();
    private final JLabel completedSessionsValue = createDashboardValue();
    private String selectedStudentId;
    private String dashboardStudentId;
    private LocalDate dashboardCalendarDate = LocalDate.now();
    private LocalDate dashboardSelectedDate = LocalDate.now();
    private CardLayout screenCardLayout;
    private JPanel screenDeck;
    private JPanel dashboardBody;
    private JPanel dashboardRoot;
    private JPanel sidebarNavigation;
    private final Map<String, JButton> navigationButtons = new LinkedHashMap<>();
    private JComboBox<StudentChoice> headerStudentCombo;
    private HeaderAvatar headerAvatar;
    private JTextField globalSearchField;
    private JButton notificationShortcutButton;
    private boolean dataReadyToSave = true;
    private boolean refreshingSessionChoices;
    private boolean refreshingNotificationChoices;
    private boolean refreshingRewardChoices;
    private boolean refreshingGroupChoices;

    public MainFrame() {
        super("Skillora | Campus Skill Exchange");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setIconImage(createWindowLogo());
        UIManager.put("OptionPane.messageFont", UI_FONT);
        UIManager.put("OptionPane.buttonFont", UI_FONT.deriveFont(Font.BOLD, 13f));
        UIManager.put("OptionPane.background", Color.WHITE);
        UIManager.put("OptionPane.messageForeground", PRIMARY_TEXT);
        UIManager.put("OptionPane.border", new RoundedContainerBorder(14));
        UIManager.put("OptionPane.buttonAreaBorder", BorderFactory.createEmptyBorder(8, 12, 4, 12));
        buildApplicationShell();

        loadSavedData();
        refreshTable(studentManager.getAllStudents());
        refreshRequestTable();
        refreshSessionChoices();
        refreshSessionTable();
        refreshNotificationChoices();
        refreshNotificationTable();
        refreshRatingStudentChoices();
        refreshRatingViews();
        refreshPortfolioStudentChoices();
        refreshPortfolioView();
        refreshRewardStudentChoices();
        refreshRewardsView();
        refreshRoadmapStudentChoices();
        refreshRoadmapTable();
        refreshGroupStudentChoices();
        refreshGroupTables();
        updateDashboard();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                closeApplication();
            }
        });
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.max(1000, Math.min(1540, screenSize.width - 80));
        int height = Math.max(680, Math.min(980, screenSize.height - 90));
        setMinimumSize(new Dimension(980, 640));
        setSize(width, height);
        setLocationRelativeTo(null);
    }

    private void buildApplicationShell() {
        JPanel application = new JPanel(new BorderLayout());
        application.setBackground(APP_BACKGROUND);
        application.add(createSidebar(), BorderLayout.WEST);

        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(APP_BACKGROUND);
        workspace.add(createTopHeader(), BorderLayout.NORTH);

        screenCardLayout = new CardLayout();
        screenDeck = new JPanel(screenCardLayout);
        screenDeck.setBackground(APP_BACKGROUND);
        addScreen(OVERVIEW_KEY, createDashboardPanel(), false);
        addScreen("Student Profiles", createProfilePanel(), true);
        addScreen("Skill Discovery", createSkillPanel(), true);
        addScreen("Skill Exchange", createExchangePanel(), true);
        addScreen("Learning Roadmaps", createRoadmapsPanel(), true);
        addScreen("Rewards & Gamification", createRewardsPanel(), true);
        addScreen("Group Learning", createGroupLearningPanel(), true);
        addScreen("Sessions", createSessionsPanel(), true);
        addScreen("Notifications", createNotificationsPanel(), true);
        addScreen("Ratings & Feedback", createRatingsPanel(), true);
        addScreen("Student Portfolio", createPortfolioPanel(), true);
        workspace.add(screenDeck, BorderLayout.CENTER);
        application.add(workspace, BorderLayout.CENTER);
        setContentPane(application);
        showScreen(OVERVIEW_KEY);
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(SIDEBAR_BACKGROUND);
        sidebar.setPreferredSize(new Dimension(244, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, LIGHT_BORDER));

        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        brand.setBorder(new EmptyBorder(25, 22, 24, 18));
        JLabel mark = new JLabel(new SkilloraLogoIcon(42));
        mark.setPreferredSize(new Dimension(42, 42));
        mark.setToolTipText("Skillora - Share skills. Grow together.");
        JLabel brandName = new JLabel("Skillora");
        brandName.setFont(UI_FONT.deriveFont(Font.BOLD, 19f));
        brandName.setForeground(PRIMARY_TEXT);
        JLabel brandCaption = new JLabel("SHARE SKILLS. GROW TOGETHER.");
        brandCaption.setFont(UI_FONT.deriveFont(Font.BOLD, 8f));
        brandCaption.setForeground(SECONDARY_TEXT);
        JPanel brandText = new JPanel();
        brandText.setOpaque(false);
        brandText.setLayout(new BoxLayout(brandText, BoxLayout.Y_AXIS));
        brandText.add(brandName);
        brandText.add(Box.createVerticalStrut(3));
        brandText.add(brandCaption);
        brand.add(mark, BorderLayout.WEST);
        brand.add(brandText, BorderLayout.CENTER);
        sidebar.add(brand, BorderLayout.NORTH);

        sidebarNavigation = new JPanel();
        sidebarNavigation.setOpaque(false);
        sidebarNavigation.setBorder(new EmptyBorder(4, 12, 12, 12));
        sidebarNavigation.setLayout(new BoxLayout(sidebarNavigation, BoxLayout.Y_AXIS));
        addNavigationButton("Overview", 0);
        addNavigationButton("Student Profiles", 1);
        addNavigationButton("Skill Discovery", 2);
        addNavigationButton("Skill Exchange", 3);
        addNavigationButton("Learning Roadmaps", 4);
        addNavigationButton("Rewards & Gamification", 5);
        addNavigationButton("Group Learning", 6);
        addNavigationButton("Sessions", 7);
        addNavigationButton("Notifications", 8);
        addNavigationButton("Ratings & Feedback", 9);
        addNavigationButton("Student Portfolio", 10);
        JScrollPane navigationScroll = new JScrollPane(sidebarNavigation);
        navigationScroll.setBorder(null);
        navigationScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        navigationScroll.getViewport().setBackground(SIDEBAR_BACKGROUND);
        navigationScroll.setBackground(SIDEBAR_BACKGROUND);
        sidebar.add(navigationScroll, BorderLayout.CENTER);

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBorder(new EmptyBorder(14, 20, 20, 20));
        footer.add(new javax.swing.JSeparator());
        footer.add(Box.createVerticalStrut(12));
        JLabel footerTitle = new JLabel("Skillora");
        footerTitle.setForeground(PRIMARY_TEXT);
        footerTitle.setFont(UI_FONT.deriveFont(Font.BOLD, 12f));
        JLabel footerText = new JLabel("Learn together. Grow together.");
        footerText.setForeground(SECONDARY_TEXT);
        footerText.setFont(UI_FONT.deriveFont(11f));
        footer.add(footerTitle);
        footer.add(Box.createVerticalStrut(4));
        footer.add(footerText);
        sidebar.add(footer, BorderLayout.SOUTH);
        return sidebar;
    }

    private void addNavigationButton(String title, int iconIndex) {
        JButton button = new JButton(title, new SidebarIcon(iconIndex, false));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setIconTextGap(13);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        button.setPreferredSize(new Dimension(216, 45));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(event -> showScreen(title));
        navigationButtons.put(title, button);
        sidebarNavigation.add(button);
        styleButton(button, false, true);
        sidebarNavigation.add(Box.createVerticalStrut(4));
    }

    private JPanel createTopHeader() {
        JPanel header = new JPanel(new BorderLayout(22, 0));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LIGHT_BORDER),
                new EmptyBorder(18, 30, 18, 28)));

        JPanel welcome = new JPanel();
        welcome.setOpaque(false);
        welcome.setLayout(new BoxLayout(welcome, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Welcome back to Skillora");
        title.setFont(UI_FONT.deriveFont(Font.BOLD, 21f));
        title.setForeground(PRIMARY_TEXT);
        JLabel subtitle = new JLabel("Let's continue your learning journey.");
        subtitle.setFont(UI_FONT.deriveFont(12.5f));
        subtitle.setForeground(SECONDARY_TEXT);
        welcome.add(title);
        welcome.add(Box.createVerticalStrut(4));
        welcome.add(subtitle);
        header.add(welcome, BorderLayout.WEST);

        globalSearchField = new JTextField(22);
        globalSearchField.setText("Search students, skills, groups");
        globalSearchField.setForeground(MUTED_TEXT);
        globalSearchField.setToolTipText("Search students, skills, and learning groups");
        globalSearchField.setPreferredSize(new Dimension(240, 42));
        globalSearchField.setMinimumSize(new Dimension(140, 42));
        globalSearchField.addActionListener(event -> searchAcrossSkillora());
        globalSearchField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent event) {
                if (globalSearchField.getText().equals("Search students, skills, groups")) {
                    globalSearchField.setText("");
                    globalSearchField.setForeground(PRIMARY_TEXT);
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent event) {
                if (globalSearchField.getText().isBlank()) {
                    globalSearchField.setText("Search students, skills, groups");
                    globalSearchField.setForeground(MUTED_TEXT);
                }
            }
        });
        header.add(globalSearchField, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        notificationShortcutButton = new JButton("Notifications");
        notificationShortcutButton.addActionListener(event -> showScreen("Notifications"));
        notificationShortcutButton.setPreferredSize(new Dimension(136, 42));
        actions.add(notificationShortcutButton);

        headerStudentCombo = new JComboBox<>();
        headerStudentCombo.setPreferredSize(new Dimension(232, 42));
        headerStudentCombo.setMinimumSize(new Dimension(210, 42));
        headerStudentCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value == null ? "Select student profile" : value.toString());
                setFont(UI_FONT.deriveFont(13f));
                setBorder(new EmptyBorder(6, 9, 6, 8));
                setToolTipText(value == null ? null : value.toString());
                return this;
            }
        });
        headerStudentCombo.addActionListener(event -> {
            if (headerStudentCombo.getSelectedItem() instanceof StudentChoice choice) {
                dashboardStudentId = choice.student().getId();
                if (headerAvatar != null) headerAvatar.setStudentName(choice.student().getName());
                refreshDashboardOverview();
            } else {
                dashboardStudentId = null;
                if (headerAvatar != null) headerAvatar.setStudentName("");
                refreshDashboardOverview();
            }
            updateNotificationShortcut();
        });
        headerAvatar = new HeaderAvatar();
        actions.add(headerAvatar);
        actions.add(headerStudentCombo);
        header.add(actions, BorderLayout.EAST);
        styleComponentTree(header);
        return header;
    }

    private void addScreen(String title, JPanel content, boolean addPageHeading) {
        JPanel page = new JPanel(new BorderLayout(0, 16));
        page.setBackground(APP_BACKGROUND);
        page.setBorder(new EmptyBorder(24, 28, 28, 28));
        if (addPageHeading) {
            JPanel heading = new JPanel();
            heading.setOpaque(false);
            heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(UI_FONT.deriveFont(Font.BOLD, 23f));
            titleLabel.setForeground(PRIMARY_TEXT);
            JLabel subtitleLabel = new JLabel(getPageSubtitle(title));
            subtitleLabel.setFont(UI_FONT.deriveFont(12f));
            subtitleLabel.setForeground(SECONDARY_TEXT);
            heading.add(titleLabel);
            heading.add(Box.createVerticalStrut(5));
            heading.add(subtitleLabel);
            page.add(heading, BorderLayout.NORTH);
        }
        JScrollPane contentScroll = new JScrollPane(content);
        contentScroll.setBorder(null);
        contentScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        contentScroll.getViewport().setBackground(APP_BACKGROUND);
        contentScroll.setBackground(APP_BACKGROUND);
        styleComponentTree(contentScroll);
        page.add(contentScroll, BorderLayout.CENTER);
        screenDeck.add(page, title);
    }

    private String getPageSubtitle(String title) {
        return switch (title) {
            case "Student Profiles" -> "Manage student information and learning interests.";
            case "Skill Discovery" -> "Explore skill offers and find compatible learning partners.";
            case "Skill Exchange" -> "Create and respond to peer-to-peer exchange requests.";
            case "Learning Roadmaps" -> "Plan learning goals and track milestone progress.";
            case "Rewards & Gamification" -> "Celebrate completed sessions, milestones, and badges.";
            case "Group Learning" -> "Discover learning circles and collaborate with peers.";
            case "Sessions" -> "Schedule and manage one-to-one skill sessions.";
            case "Notifications" -> "Review updates from your learning community.";
            case "Ratings & Feedback" -> "Share feedback and review completed exchanges.";
            case "Student Portfolio" -> "Explore student achievements and learning history.";
            default -> "";
        };
    }

    private void showScreen(String title) {
        if (screenDeck == null) return;
        screenCardLayout.show(screenDeck, title);
        navigationButtons.forEach((name, button) -> {
            boolean selected = name.equals(title);
            button.setBackground(selected ? SELECTED_NAV_BACKGROUND : Color.WHITE);
            button.setForeground(selected ? PRIMARY_PURPLE : SECONDARY_TEXT);
            button.setIcon(new SidebarIcon(new ArrayList<>(navigationButtons.keySet()).indexOf(name), selected));
            button.setFont(UI_FONT.deriveFont(selected ? Font.BOLD : Font.PLAIN, 12.5f));
            button.repaint();
        });
    }

    private void refreshHeaderStudentChoices() {
        String oldSelection = dashboardStudentId;
        List<StudentChoice> choices = studentManager.getAllStudents().stream()
                .map(StudentChoice::new).toList();
        headerStudentCombo.setModel(new DefaultComboBoxModel<>(choices.toArray(StudentChoice[]::new)));
        if (oldSelection != null && choices.stream()
                .anyMatch(choice -> choice.student().getId().equalsIgnoreCase(oldSelection))) {
            for (int i = 0; i < headerStudentCombo.getItemCount(); i++) {
                if (headerStudentCombo.getItemAt(i).student().getId().equalsIgnoreCase(oldSelection)) {
                    headerStudentCombo.setSelectedIndex(i);
                    break;
                }
            }
        } else if (!choices.isEmpty()) {
            headerStudentCombo.setSelectedIndex(0);
        } else {
            dashboardStudentId = null;
        }
        StudentChoice selected = (StudentChoice) headerStudentCombo.getSelectedItem();
        dashboardStudentId = selected == null ? null : selected.student().getId();
        if (headerAvatar != null) {
            headerAvatar.setStudentName(selected == null ? "" : selected.student().getName());
        }
        updateNotificationShortcut();
    }

    private void updateNotificationShortcut() {
        if (notificationShortcutButton == null) return;
        int unread = dashboardStudentId == null
                ? (int) notificationManager.getAllNotifications().stream()
                    .filter(notification -> !notification.isRead()).count()
                : notificationManager.getUnreadCount(dashboardStudentId);
        notificationShortcutButton.setText(unread > 0 ? "Notifications  •  " + unread : "Notifications");
        notificationShortcutButton.setForeground(unread > 0 ? PRIMARY_PURPLE : PRIMARY_TEXT);
    }

    private void searchAcrossSkillora() {
        String query = globalSearchField.getText().trim().toLowerCase(Locale.ROOT);
        if (query.isBlank() || query.equals("search students, skills, groups")) return;
        record SearchResult(String label, String destination) {
            @Override public String toString() { return label; }
        }
        List<SearchResult> matches = new ArrayList<>();
        studentManager.getAllStudents().stream()
                .filter(student -> student.getName().toLowerCase(Locale.ROOT).contains(query)
                        || student.getId().toLowerCase(Locale.ROOT).contains(query))
                .forEach(student -> matches.add(new SearchResult(
                        "Student: " + student.getName() + " (" + student.getId() + ")", "Student Profiles")));
        studentManager.getAllStudents().forEach(student -> {
            java.util.stream.Stream.concat(student.getSkillsToTeach().stream(), student.getSkillsToLearn().stream())
                    .filter(skill -> skill.getName().toLowerCase(Locale.ROOT).contains(query))
                    .forEach(skill -> matches.add(new SearchResult("Skill: " + skill.getName()
                            + " — " + student.getName(), "Skill Discovery")));
        });
        groupLearningManager.getAllGroups().stream()
                .filter(group -> group.getName().toLowerCase(Locale.ROOT).contains(query)
                        || group.getLearningTopic().toLowerCase(Locale.ROOT).contains(query))
                .forEach(group -> matches.add(new SearchResult("Learning group: " + group.getName()
                        + " — " + group.getLearningTopic(), "Group Learning")));
        if (matches.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No students, skills, or groups matched \"" + query + "\".",
                    "Skillora Search", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Object selected = JOptionPane.showInputDialog(this, "Search results",
                "Skillora Search", JOptionPane.PLAIN_MESSAGE, null,
                matches.toArray(), matches.get(0));
        if (selected instanceof SearchResult result) showScreen(result.destination());
    }

    private JPanel createDashboardPanel() {
        dashboardRoot = new JPanel(new BorderLayout());
        dashboardRoot.setOpaque(false);
        dashboardBody = new JPanel(new BorderLayout(20, 18));
        dashboardBody.setOpaque(false);
        dashboardRoot.add(dashboardBody, BorderLayout.CENTER);
        return dashboardRoot;
    }

    private JPanel createDashboardCard(String title, JLabel valueLabel) {
        Color accent = dashboardCardAccent(title);
        JPanel card = new RoundedPanel(18, Color.WHITE, accent);
        card.setBorder(new EmptyBorder(18, 18, 16, 16));
        card.setLayout(new BorderLayout(8, 8));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(accent);
        titleLabel.setFont(UI_FONT.deriveFont(11.5f));
        valueLabel.setHorizontalAlignment(SwingConstants.LEFT);
        valueLabel.setForeground(PRIMARY_TEXT);
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private Color dashboardCardAccent(String title) {
        return switch (title) {
            case "Total students" -> ACCENT_BLUE;
            case "Available skills" -> PRIMARY_PURPLE;
            case "Completed sessions" -> ACCENT_MINT;
            case "Accepted exchanges" -> ACCENT_PEACH;
            default -> PRIMARY_PURPLE;
        };
    }

    private JLabel createDashboardValue() {
        JLabel valueLabel = new JLabel("0");
        valueLabel.setFont(UI_FONT.deriveFont(Font.BOLD, 25f));
        valueLabel.setForeground(PRIMARY_TEXT);
        return valueLabel;
    }

    private void loadSavedData() {
        try {
            FileManager.AllData loadedData = fileManager.loadAllData();
            studentManager.loadStudents(loadedData.students());
            List<String> warnings = new java.util.ArrayList<>(loadedData.warnings());
            for (FileManager.SavedRequest savedRequest : loadedData.requests()) {
                try {
                    exchangeRequestManager.restoreRequest(
                            savedRequest.requestId(),
                            savedRequest.requesterId(),
                            savedRequest.recipientId(),
                            savedRequest.offeredSkillName(),
                            savedRequest.requestedSkillName(),
                            savedRequest.status());
                } catch (IllegalArgumentException exception) {
                    warnings.add("Request #" + savedRequest.requestId() + ": "
                            + exception.getMessage() + " Skipped.");
                }
            }
            for (FileManager.SavedSession savedSession : loadedData.sessions()) {
                try {
                    sessionManager.restoreSession(savedSession.sessionId(), savedSession.teacherId(),
                            savedSession.learnerId(), savedSession.skillName(), savedSession.date(),
                            savedSession.startTime(), savedSession.durationMinutes(), savedSession.status());
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    warnings.add("Session #" + savedSession.sessionId() + ": "
                            + exception.getMessage() + " Skipped.");
                }
            }
            for (FileManager.SavedRating savedRating : loadedData.ratings()) {
                try {
                    ratingManager.restoreRating(savedRating.sessionId(), savedRating.reviewerId(),
                            savedRating.score(), savedRating.feedback(), savedRating.createdAt());
                } catch (IllegalArgumentException | IllegalStateException
                         | java.util.NoSuchElementException exception) {
                    warnings.add("Rating for session #" + savedRating.sessionId() + ": "
                            + exception.getMessage() + " Skipped.");
                }
            }
            for (FileManager.SavedNotification savedNotification : loadedData.notifications()) {
                try {
                    notificationManager.restoreNotification(savedNotification.notificationId(),
                            savedNotification.recipientId(), savedNotification.eventType(),
                            savedNotification.message(), savedNotification.createdAt(), savedNotification.read());
                } catch (IllegalArgumentException exception) {
                    warnings.add("Notification #" + savedNotification.notificationId() + ": "
                            + exception.getMessage() + " Skipped.");
                }
            }
            for (FileManager.SavedRoadmap savedRoadmap : loadedData.roadmaps()) {
                try {
                    List<LearningRoadmapManager.SavedMilestone> milestones = savedRoadmap.milestones().stream()
                            .map(milestone -> new LearningRoadmapManager.SavedMilestone(
                                    milestone.milestoneId(), milestone.title(), milestone.description(),
                                    milestone.dueDate(), milestone.completed()))
                            .toList();
                    learningRoadmapManager.restoreRoadmap(savedRoadmap.roadmapId(), savedRoadmap.studentId(),
                            savedRoadmap.title(), savedRoadmap.targetSkill(), savedRoadmap.description(),
                            savedRoadmap.creationDate(), savedRoadmap.targetCompletionDate(), milestones);
                } catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
                    warnings.add("Roadmap #" + savedRoadmap.roadmapId() + ": "
                            + exception.getMessage() + " Skipped.");
                }
            }
            for (FileManager.SavedRewardTransaction saved : loadedData.rewardTransactions()) {
                try {
                    rewardManager.restoreTransaction(saved.transactionId(), saved.studentId(),
                            saved.eventReference(), saved.description(), saved.points(), saved.createdAt());
                } catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
                    warnings.add("Reward transaction #" + saved.transactionId() + ": "
                            + exception.getMessage() + " Skipped.");
                }
            }
            for (FileManager.SavedBadge saved : loadedData.badges()) {
                try {
                    rewardManager.restoreBadge(saved.badgeId(), saved.studentId(),
                            saved.badgeName(), saved.dateEarned());
                } catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
                    warnings.add("Achievement badge " + saved.badgeId() + ": "
                            + exception.getMessage() + " Skipped.");
                }
            }
            for (FileManager.SavedLearningGroup saved : loadedData.groups()) {
                try {
                    groupLearningManager.restoreGroup(saved.groupId(), saved.name(), saved.description(),
                            saved.topic(), saved.ownerId(), saved.creationDate(), saved.maximumMembers(),
                            saved.status(), saved.memberIds());
                } catch (IllegalArgumentException | IllegalStateException
                         | java.util.NoSuchElementException exception) {
                    warnings.add("Learning group #" + saved.groupId() + ": "
                            + exception.getMessage() + " Skipped.");
                }
            }
            for (FileManager.SavedGroupLearningSession saved : loadedData.groupSessions()) {
                try {
                    groupLearningManager.restoreSession(saved.sessionId(), saved.groupId(), saved.title(),
                            saved.description(), saved.date(), saved.time(), saved.durationMinutes(),
                            saved.organizerId(), saved.status());
                } catch (IllegalArgumentException | IllegalStateException
                         | java.util.NoSuchElementException exception) {
                    warnings.add("Group session #" + saved.sessionId() + ": "
                            + exception.getMessage() + " Skipped.");
                }
            }
            dataReadyToSave = warnings.isEmpty();
            showLoadWarnings(warnings);
            if (dataReadyToSave) {
                RewardManager.RewardState before = rewardManager.snapshot();
                rewardManager.synchronizeCompletedActivities();
                if ((rewardManager.getAllTransactions().size() != before.transactions().size()
                        || rewardManager.getAllBadges().size() != before.badges().size())
                        && !saveData()) {
                    rewardManager.restoreState(before);
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            dataReadyToSave = false;
            JOptionPane.showMessageDialog(
                    this,
                    "Saved data could not be loaded. The application will start with empty data.\n"
                            + "Saving is disabled to protect the existing files.\n"
                            + exception.getMessage(),
                    "Data Load Problem",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showLoadWarnings(List<String> warnings) {
        if (warnings.isEmpty()) {
            return;
        }
        String warningText = String.join("\n", warnings);
        if (warningText.length() > 2500) {
            warningText = warningText.substring(0, 2500) + "\nMore warnings were omitted.";
        }
        JOptionPane.showMessageDialog(
                this,
                "Some saved records could not be restored:\n" + warningText
                        + "\nSaving is disabled to protect the existing data files.",
                "Data Load Warning",
                JOptionPane.WARNING_MESSAGE);
    }

    private boolean saveData() {
        if (!dataReadyToSave) {
            JOptionPane.showMessageDialog(
                    this,
                    "Changes cannot be saved because one or more saved records could not be loaded or restored.\n"
                            + "Saving is disabled to protect the existing files.",
                    "Data Save Disabled",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        try {
            fileManager.saveAllData(studentManager.getAllStudents(), exchangeRequestManager.getAllRequests(),
                    sessionManager.getAllSessions(), ratingManager.getAllRatings(),
                    notificationManager.getAllNotifications(), learningRoadmapManager.getAllRoadmaps(),
                    rewardManager.getAllTransactions(), rewardManager.getAllBadges(),
                    groupLearningManager.getAllGroups(), groupLearningManager.getAllSessions());
            updateDashboard();
            return true;
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    "Changes are currently in memory but could not be saved.\n"
                            + exception.getMessage(),
                    "Data Save Problem",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void closeApplication() {
        if (saveData()) {
            dispose();
            return;
        }
        int choice = JOptionPane.showConfirmDialog(
                this,
                "The latest changes could not be saved. Close without saving?",
                "Unsaved Changes",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            dispose();
        }
    }

    private void updateDashboard() {
        List<Student> students = studentManager.getAllStudents();
        Set<Skill> availableSkills = new HashSet<>();
        for (Student student : students) {
            availableSkills.addAll(student.getSkillsToTeach());
        }
        List<ExchangeRequest> requests = exchangeRequestManager.getAllRequests();
        long pendingCount = requests.stream()
                .filter(request -> request.getStatus() == ExchangeRequest.Status.PENDING)
                .count();
        long acceptedCount = requests.stream()
                .filter(request -> request.getStatus() == ExchangeRequest.Status.ACCEPTED)
                .count();
        long completedCount = sessionManager.getAllSessions().stream()
                .filter(session -> session.getStatus() == Session.Status.COMPLETED)
                .count();
        completedCount += groupLearningManager.getAllSessions().stream()
                .filter(session -> session.getStatus() == GroupLearningSession.Status.COMPLETED)
                .count();

        totalStudentsValue.setText(Integer.toString(students.size()));
        availableSkillsValue.setText(Integer.toString(availableSkills.size()));
        pendingRequestsValue.setText(Long.toString(pendingCount));
        acceptedExchangesValue.setText(Long.toString(acceptedCount));
        completedSessionsValue.setText(Long.toString(completedCount));
        refreshHeaderStudentChoices();
        refreshDashboardOverview();
    }

    private void refreshDashboardOverview() {
        if (dashboardBody == null) return;
        dashboardBody.removeAll();

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(createQuickStats());
        center.add(Box.createVerticalStrut(18));

        JPanel activityRow = new JPanel(new GridLayout(1, 2, 16, 0));
        activityRow.setOpaque(false);
        activityRow.add(createLearningActivityCard());
        activityRow.add(createLearningProgressCard());
        activityRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 264));
        center.add(activityRow);
        center.add(Box.createVerticalStrut(18));
        center.add(createContinueLearningCard());
        center.add(Box.createVerticalGlue());

        JPanel information = new JPanel();
        information.setOpaque(false);
        information.setPreferredSize(new Dimension(320, 0));
        information.setLayout(new BoxLayout(information, BoxLayout.Y_AXIS));
        information.add(createStudentSummaryCard());
        information.add(Box.createVerticalStrut(14));
        information.add(createCalendarCard());
        information.add(Box.createVerticalStrut(14));
        information.add(createTodayPlanCard());
        information.add(Box.createVerticalStrut(14));
        information.add(createAchievementsCard());
        information.add(Box.createVerticalGlue());

        dashboardBody.add(center, BorderLayout.CENTER);
        dashboardBody.add(information, BorderLayout.EAST);
        styleComponentTree(dashboardBody);
        dashboardBody.revalidate();
        dashboardBody.repaint();
    }

    private JPanel createQuickStats() {
        JPanel stats = new JPanel(new GridLayout(1, 4, 12, 0));
        stats.setOpaque(false);
        stats.add(createDashboardCard("Total students", totalStudentsValue));
        stats.add(createDashboardCard("Available skills", availableSkillsValue));
        stats.add(createDashboardCard("Completed sessions", completedSessionsValue));
        stats.add(createDashboardCard("Accepted exchanges", acceptedExchangesValue));
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 94));
        return stats;
    }

    private JPanel createLearningActivityCard() {
        JPanel card = createRoundedCard();
        card.setLayout(new BorderLayout(8, 8));
        card.add(createCardHeading("Learning Activity", "Completed and scheduled sessions this week"),
                BorderLayout.NORTH);
        ActivityChart chart = new ActivityChart(sessionManager.getAllSessions(),
                groupLearningManager.getAllSessions());
        chart.setPreferredSize(new Dimension(320, 176));
        card.add(chart, BorderLayout.CENTER);
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        legend.setOpaque(false);
        legend.add(createLegendItem("Completed", PRIMARY_PURPLE));
        legend.add(createLegendItem("Scheduled", new Color(0xC6B8FF)));
        card.add(legend, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createLearningProgressCard() {
        JPanel card = createRoundedCard();
        card.setLayout(new BorderLayout(8, 8));
        card.add(createCardHeading("Learning Progress", "Milestones completed across all roadmaps"),
                BorderLayout.NORTH);
        int completed = 0;
        int milestoneCount = 0;
        for (LearningRoadmap roadmap : learningRoadmapManager.getAllRoadmaps()) {
            milestoneCount += roadmap.getMilestones().size();
            completed += (int) roadmap.getMilestones().stream()
                    .filter(RoadmapMilestone::isCompleted).count();
        }
        int percent = milestoneCount == 0 ? 0 : completed * 100 / milestoneCount;
        JPanel progressCenter = new JPanel(new GridBagLayout());
        progressCenter.setOpaque(false);
        CircularProgress indicator = new CircularProgress(percent);
        indicator.setPreferredSize(new Dimension(128, 128));
        progressCenter.add(indicator);
        card.add(progressCenter, BorderLayout.CENTER);
        JLabel caption = new JLabel(milestoneCount == 0 ? "No roadmap milestones yet"
                : completed + " of " + milestoneCount + " milestones complete", SwingConstants.CENTER);
        caption.setForeground(SECONDARY_TEXT);
        caption.setFont(UI_FONT.deriveFont(11f));
        card.add(caption, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createContinueLearningCard() {
        JPanel card = createRoundedCard();
        card.setLayout(new BorderLayout(12, 12));
        card.add(createCardHeading("Continue Learning", "Pick up where you left off"), BorderLayout.NORTH);

        List<LearningRoadmap> active = learningRoadmapManager.getAllRoadmaps().stream()
                .filter(roadmap -> roadmap.getStatus() != LearningRoadmap.Status.COMPLETED)
                .filter(roadmap -> dashboardStudentId == null
                        || roadmap.getStudentId().equalsIgnoreCase(dashboardStudentId))
                .limit(4).toList();
        JPanel items = new JPanel();
        items.setOpaque(false);
        items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
        if (active.isEmpty()) {
            items.add(createEmptyState("No active roadmaps",
                    dashboardStudentId == null
                            ? "Create a roadmap to start tracking learning progress."
                            : "This student's active learning roadmaps will appear here."));
        } else {
            for (LearningRoadmap roadmap : active) {
                items.add(createRoadmapPreview(roadmap));
                items.add(Box.createVerticalStrut(9));
            }
        }
        card.add(items, BorderLayout.CENTER);
        return card;
    }

    private JPanel createRoadmapPreview(LearningRoadmap roadmap) {
        JPanel item = new JPanel(new BorderLayout(10, 5));
        item.setOpaque(false);
        item.setBorder(new EmptyBorder(7, 0, 7, 0));
        JPanel titleColumn = new JPanel();
        titleColumn.setOpaque(false);
        titleColumn.setLayout(new BoxLayout(titleColumn, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(roadmap.getTitle());
        title.setForeground(PRIMARY_TEXT);
        title.setFont(UI_FONT.deriveFont(Font.BOLD, 13f));
        JLabel target = new JLabel(roadmap.getTargetSkill() + "  ·  "
                + roadmap.getProgressPercent() + "% complete");
        target.setForeground(SECONDARY_TEXT);
        target.setFont(UI_FONT.deriveFont(11f));
        titleColumn.add(title);
        titleColumn.add(Box.createVerticalStrut(4));
        titleColumn.add(target);
        JProgressBar progress = new JProgressBar(0, 100);
        progress.setValue(roadmap.getProgressPercent());
        progress.setStringPainted(false);
        progress.setPreferredSize(new Dimension(128, 8));
        progress.setBorderPainted(false);
        progress.setForeground(PRIMARY_PURPLE);
        progress.setBackground(SELECTED_NAV_BACKGROUND);
        JButton open = new JButton("Open");
        open.addActionListener(event -> openRoadmap(roadmap));
        JPanel trailing = new JPanel(new BorderLayout(8, 5));
        trailing.setOpaque(false);
        trailing.add(progress, BorderLayout.CENTER);
        trailing.add(open, BorderLayout.EAST);
        item.add(titleColumn, BorderLayout.CENTER);
        item.add(trailing, BorderLayout.SOUTH);
        return item;
    }

    private void openRoadmap(LearningRoadmap roadmap) {
        showScreen("Learning Roadmaps");
        selectRoadmapStudent(roadmap.getStudentId());
        refreshRoadmapTable();
        for (int row = 0; row < roadmapTableModel.getRowCount(); row++) {
            if (Integer.valueOf(roadmap.getRoadmapId()).equals(roadmapTableModel.getValueAt(row, 0))) {
                roadmapTable.setRowSelectionInterval(row, row);
                roadmapTable.scrollRectToVisible(roadmapTable.getCellRect(row, 0, true));
                break;
            }
        }
    }

    private JPanel createStudentSummaryCard() {
        JPanel card = createRoundedCard();
        card.setLayout(new BorderLayout(10, 10));
        card.add(createCardHeading("Student Profile", "Selected learning profile"), BorderLayout.NORTH);
        Student student = findDashboardStudent();
        if (student == null) {
            card.add(createEmptyState("No student profiles", "Register a student to see profile details."),
                    BorderLayout.CENTER);
            return card;
        }
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JPanel identity = new JPanel(new BorderLayout(10, 0));
        identity.setOpaque(false);
        identity.add(new Avatar(student.getName()), BorderLayout.WEST);
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(student.getName());
        name.setForeground(PRIMARY_TEXT);
        name.setFont(UI_FONT.deriveFont(Font.BOLD, 14f));
        JLabel department = new JLabel(student.getDepartment() + " · Year " + student.getYearOfStudy());
        department.setForeground(SECONDARY_TEXT);
        department.setFont(UI_FONT.deriveFont(10.5f));
        JLabel email = new JLabel(student.getEmail());
        email.setForeground(SECONDARY_TEXT);
        email.setFont(UI_FONT.deriveFont(10f));
        info.add(name);
        info.add(Box.createVerticalStrut(3));
        info.add(department);
        info.add(Box.createVerticalStrut(2));
        info.add(email);
        identity.add(info, BorderLayout.CENTER);
        content.add(identity);
        content.add(Box.createVerticalStrut(12));
        content.add(createSkillSummary("SKILLS OFFERED", student.getSkillsToTeach().stream()
                .map(Skill::getName).toList()));
        content.add(Box.createVerticalStrut(8));
        content.add(createSkillSummary("WANTS TO LEARN", student.getSkillsToLearn().stream()
                .map(Skill::getName).toList()));
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel createSkillSummary(String title, List<String> skills) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(title);
        heading.setForeground(SECONDARY_TEXT);
        heading.setFont(UI_FONT.deriveFont(Font.BOLD, 9.5f));
        JTextArea values = new JTextArea(skills.isEmpty() ? "None added" : String.join(", ", skills));
        values.setEditable(false);
        values.setLineWrap(true);
        values.setWrapStyleWord(true);
        values.setOpaque(false);
        values.setForeground(skills.isEmpty() ? SECONDARY_TEXT : PRIMARY_TEXT);
        values.setFont(UI_FONT.deriveFont(11f));
        values.setBorder(null);
        values.putClientProperty("skillora.plain", Boolean.TRUE);
        section.add(heading);
        section.add(Box.createVerticalStrut(3));
        section.add(values);
        return section;
    }

    private JPanel createCalendarCard() {
        JPanel card = createRoundedCard();
        card.setLayout(new BorderLayout(7, 8));
        JPanel monthBar = new JPanel(new BorderLayout());
        monthBar.setOpaque(false);
        JLabel month = new JLabel(dashboardCalendarDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        month.setFont(UI_FONT.deriveFont(Font.BOLD, 13f));
        month.setForeground(PRIMARY_TEXT);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 1, 0));
        controls.setOpaque(false);
        JButton previous = new JButton("‹");
        JButton next = new JButton("›");
        previous.addActionListener(event -> changeCalendarMonth(-1));
        next.addActionListener(event -> changeCalendarMonth(1));
        controls.add(previous);
        controls.add(next);
        monthBar.add(month, BorderLayout.WEST);
        monthBar.add(controls, BorderLayout.EAST);
        card.add(monthBar, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
        grid.setOpaque(false);
        for (String day : List.of("M", "T", "W", "T", "F", "S", "S")) {
            JLabel label = new JLabel(day, SwingConstants.CENTER);
            label.setForeground(SECONDARY_TEXT);
            label.setFont(UI_FONT.deriveFont(Font.BOLD, 9f));
            grid.add(label);
        }
        YearMonth yearMonth = YearMonth.from(dashboardCalendarDate);
        int leading = yearMonth.atDay(1).getDayOfWeek().getValue() - 1;
        for (int i = 0; i < leading; i++) grid.add(new JLabel(""));
        Set<LocalDate> scheduledDates = sessionManager.getAllSessions().stream()
                .filter(session -> session.getStatus() == Session.Status.SCHEDULED)
                .filter(this::isForDashboardStudent)
                .map(Session::getDate).collect(Collectors.toSet());
        groupLearningManager.getAllSessions().stream()
                .filter(session -> session.getStatus() == GroupLearningSession.Status.SCHEDULED)
                .filter(this::isForDashboardStudent)
                .map(GroupLearningSession::getScheduledDate)
                .forEach(scheduledDates::add);
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            JButton dateButton = new JButton(Integer.toString(day));
            dateButton.setFont(UI_FONT.deriveFont(10.5f));
            dateButton.setToolTipText(scheduledDates.contains(date) ? "Scheduled session" : null);
            if (scheduledDates.contains(date)) {
                dateButton.setBackground(SELECTED_NAV_BACKGROUND);
                dateButton.setForeground(PRIMARY_PURPLE);
                dateButton.setFont(UI_FONT.deriveFont(Font.BOLD, 10.5f));
            } else if (date.equals(LocalDate.now())) {
                dateButton.setForeground(PRIMARY_PURPLE);
            }
            if (date.equals(dashboardSelectedDate)) {
                dateButton.setBackground(PRIMARY_PURPLE);
                dateButton.setForeground(Color.WHITE);
            }
            dateButton.addActionListener(event -> {
                dashboardSelectedDate = date;
                refreshDashboardOverview();
            });
            grid.add(dateButton);
        }
        card.add(grid, BorderLayout.CENTER);
        JLabel selectedDay = new JLabel("Selected: " + dashboardSelectedDate.format(
                DateTimeFormatter.ofPattern("EEE, MMM d")));
        selectedDay.setForeground(SECONDARY_TEXT);
        selectedDay.setFont(UI_FONT.deriveFont(10f));
        card.add(selectedDay, BorderLayout.SOUTH);
        return card;
    }

    private void changeCalendarMonth(int offset) {
        YearMonth nextMonth = YearMonth.from(dashboardCalendarDate).plusMonths(offset);
        int day = Math.min(dashboardSelectedDate.getDayOfMonth(), nextMonth.lengthOfMonth());
        dashboardCalendarDate = nextMonth.atDay(1);
        dashboardSelectedDate = nextMonth.atDay(day);
        refreshDashboardOverview();
    }

    private JPanel createTodayPlanCard() {
        JPanel card = createRoundedCard();
        card.setLayout(new BorderLayout(8, 8));
        String headingDate = dashboardSelectedDate.equals(LocalDate.now())
                ? "Today’s Plan" : "Plan for " + dashboardSelectedDate.format(
                        DateTimeFormatter.ofPattern("MMM d"));
        card.add(createCardHeading(headingDate, "Sessions and roadmap milestones"), BorderLayout.NORTH);
        JPanel items = new JPanel();
        items.setOpaque(false);
        items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
        LocalDate start = dashboardSelectedDate;
        LocalDate end = dashboardSelectedDate.equals(LocalDate.now()) ? start.plusDays(7) : start;
        List<Session> sessions = sessionManager.getAllSessions().stream()
                .filter(session -> session.getStatus() == Session.Status.SCHEDULED)
                .filter(this::isForDashboardStudent)
                .filter(session -> !session.getDate().isBefore(start) && !session.getDate().isAfter(end))
                .sorted(Comparator.comparing(Session::getDate).thenComparing(Session::getStartTime))
                .limit(3).toList();
        List<GroupLearningSession> circleSessions = groupLearningManager.getAllSessions().stream()
                .filter(session -> session.getStatus() == GroupLearningSession.Status.SCHEDULED)
                .filter(this::isForDashboardStudent)
                .filter(session -> !session.getScheduledDate().isBefore(start)
                        && !session.getScheduledDate().isAfter(end))
                .sorted(Comparator.comparing(GroupLearningSession::getScheduledDate)
                        .thenComparing(GroupLearningSession::getScheduledTime))
                .limit(3).toList();
        List<RoadmapMilestone> milestones = learningRoadmapManager.getAllRoadmaps().stream()
                .filter(roadmap -> dashboardStudentId == null
                        || roadmap.getStudentId().equalsIgnoreCase(dashboardStudentId))
                .flatMap(roadmap -> roadmap.getMilestones().stream()
                        .filter(milestone -> !milestone.isCompleted())
                        .filter(milestone -> !milestone.getDueDate().isBefore(start)
                                && !milestone.getDueDate().isAfter(end)))
                .sorted(Comparator.comparing(RoadmapMilestone::getDueDate))
                .limit(3).toList();
        int count = 0;
        for (Session session : sessions) {
            String participants = session.getTeacher().getName() + " / " + session.getLearner().getName();
            items.add(createPlanEntry(session.getSkill().getName(), participants,
                    session.getDate().format(DateTimeFormatter.ofPattern("MMM d"))
                            + " · " + session.getStartTime().format(DateTimeFormatter.ofPattern("h:mm a"))));
            count++;
        }
        for (GroupLearningSession session : circleSessions) {
            String groupName = groupLearningManager.getGroup(session.getGroupId()).getName();
            items.add(createPlanEntry(session.getTitle(), groupName + " · Group session",
                    session.getScheduledDate().format(DateTimeFormatter.ofPattern("MMM d"))
                            + " · " + session.getScheduledTime().format(DateTimeFormatter.ofPattern("h:mm a"))));
            count++;
        }
        for (RoadmapMilestone milestone : milestones) {
            items.add(createPlanEntry(milestone.getTitle(), "Roadmap milestone",
                    "Due " + milestone.getDueDate().format(DateTimeFormatter.ofPattern("MMM d"))));
            count++;
        }
        if (count == 0) items.add(createEmptyState("Nothing scheduled",
                dashboardSelectedDate.equals(LocalDate.now())
                        ? "No upcoming sessions or milestones in the next 7 days."
                        : "No sessions or incomplete milestones on this date."));
        card.add(items, BorderLayout.CENTER);
        return card;
    }

    private JPanel createPlanEntry(String title, String detail, String date) {
        JPanel entry = new JPanel(new BorderLayout(2, 2));
        entry.setOpaque(false);
        entry.setBorder(new EmptyBorder(4, 0, 5, 0));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(PRIMARY_TEXT);
        titleLabel.setFont(UI_FONT.deriveFont(Font.BOLD, 11f));
        JLabel detailLabel = new JLabel(detail + " · " + date);
        detailLabel.setForeground(SECONDARY_TEXT);
        detailLabel.setFont(UI_FONT.deriveFont(9.5f));
        entry.add(titleLabel, BorderLayout.NORTH);
        entry.add(detailLabel, BorderLayout.CENTER);
        return entry;
    }

    private JPanel createAchievementsCard() {
        JPanel card = createRoundedCard();
        card.setLayout(new BorderLayout(8, 8));
        card.add(createCardHeading("Achievements", "Badges and reward points"), BorderLayout.NORTH);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        Student student = findDashboardStudent();
        if (student == null) {
            content.add(createEmptyState("No student selected", "Choose a profile to see achievements."));
        } else {
            JLabel points = new JLabel(rewardManager.getTotalPoints(student.getId()) + " reward points");
            points.setForeground(PRIMARY_PURPLE);
            points.setFont(UI_FONT.deriveFont(Font.BOLD, 14f));
            content.add(points);
            List<model.AchievementBadge> badges = rewardManager.getBadgesForStudent(student.getId());
            if (badges.isEmpty()) {
                content.add(Box.createVerticalStrut(8));
                content.add(createSmallText("Badges earned through completed learning activities will appear here."));
            } else {
                for (model.AchievementBadge badge : badges.stream().limit(3).toList()) {
                    content.add(Box.createVerticalStrut(6));
                    content.add(createSmallText("✦  " + badge.getBadgeName()));
                }
            }
        }
        JButton rewards = new JButton("View rewards");
        rewards.addActionListener(event -> showScreen("Rewards & Gamification"));
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        footer.setOpaque(false);
        footer.add(rewards);
        card.add(content, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createEmptyState(String heading, String detail) {
        JPanel empty = new JPanel();
        empty.setOpaque(false);
        empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(heading);
        title.setForeground(PRIMARY_TEXT);
        title.setFont(UI_FONT.deriveFont(Font.BOLD, 12f));
        JTextArea description = new JTextArea(detail);
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setOpaque(false);
        description.setForeground(SECONDARY_TEXT);
        description.setFont(UI_FONT.deriveFont(10.5f));
        description.setBorder(null);
        description.putClientProperty("skillora.plain", Boolean.TRUE);
        empty.add(title);
        empty.add(Box.createVerticalStrut(4));
        empty.add(description);
        return empty;
    }

    private JLabel createSmallText(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(SECONDARY_TEXT);
        label.setFont(UI_FONT.deriveFont(10.5f));
        return label;
    }

    private JPanel createCardHeading(String title, String subtitle) {
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(PRIMARY_TEXT);
        titleLabel.setFont(UI_FONT.deriveFont(Font.BOLD, 14f));
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setForeground(SECONDARY_TEXT);
        subtitleLabel.setFont(UI_FONT.deriveFont(10f));
        heading.add(titleLabel);
        heading.add(Box.createVerticalStrut(4));
        heading.add(subtitleLabel);
        return heading;
    }

    private JPanel createLegendItem(String text, Color color) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        item.setOpaque(false);
        JLabel dot = new JLabel("●");
        dot.setForeground(color);
        dot.setFont(UI_FONT.deriveFont(9f));
        JLabel label = new JLabel(text);
        label.setForeground(SECONDARY_TEXT);
        label.setFont(UI_FONT.deriveFont(10f));
        item.add(dot);
        item.add(label);
        return item;
    }

    private JPanel createRoundedCard() {
        JPanel card = new RoundedPanel(18);
        card.setBorder(new EmptyBorder(18, 18, 16, 18));
        card.setMinimumSize(new Dimension(0, 0));
        return card;
    }

    private JPanel createSegmentedCards(String[] names, Component[] content) {
        JPanel selectorPage = new JPanel(new BorderLayout(0, 8));
        selectorPage.setOpaque(false);
        JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        selector.setOpaque(false);
        CardLayout cardLayout = new CardLayout();
        JPanel cardDeck = new JPanel(cardLayout);
        cardDeck.setOpaque(false);
        Map<String, JButton> selectors = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            JButton button = new JButton(name);
            button.setPreferredSize(new Dimension(Math.max(125, name.length() * 8 + 22), 36));
            button.addActionListener(event -> {
                cardLayout.show(cardDeck, name);
                selectors.forEach((key, selectorButton) -> {
                    boolean selected = key.equals(name);
                    selectorButton.setBackground(selected ? SELECTED_NAV_BACKGROUND : CARD_BACKGROUND);
                    selectorButton.setForeground(selected ? PRIMARY_PURPLE : SECONDARY_TEXT);
                    selectorButton.setFont(UI_FONT.deriveFont(selected ? Font.BOLD : Font.PLAIN, 11f));
                });
            });
            selectors.put(name, button);
            styleButton(button, false, true);
            selector.add(button);
            cardDeck.add(content[i], name);
        }
        selectorPage.add(selector, BorderLayout.NORTH);
        selectorPage.add(cardDeck, BorderLayout.CENTER);
        if (names.length > 0) {
            selectors.get(names[0]).doClick();
        }
        return selectorPage;
    }

    private Student findDashboardStudent() {
        return dashboardStudentId == null ? null
                : studentManager.findStudentById(dashboardStudentId).orElse(null);
    }

    private boolean isForDashboardStudent(Session session) {
        return dashboardStudentId == null
                || session.getTeacher().getId().equalsIgnoreCase(dashboardStudentId)
                || session.getLearner().getId().equalsIgnoreCase(dashboardStudentId);
    }

    private boolean isForDashboardStudent(GroupLearningSession session) {
        return dashboardStudentId == null
                || groupLearningManager.getGroup(session.getGroupId()).hasMember(dashboardStudentId);
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        result.append(Character.toUpperCase(parts[0].charAt(0)));
        if (parts.length > 1) result.append(Character.toUpperCase(parts[parts.length - 1].charAt(0)));
        return result.toString();
    }

    private final class ActivityChart extends JPanel {
        private final List<Session> sessions;
        private final List<GroupLearningSession> groupSessions;

        private ActivityChart(List<Session> sessions, List<GroupLearningSession> groupSessions) {
            this.sessions = sessions;
            this.groupSessions = groupSessions;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left = 28, top = 10, bottom = getHeight() - 28, chartWidth = getWidth() - left - 12;
            g.setColor(LIGHT_BORDER);
            for (int line = 0; line < 4; line++) {
                int y = top + (bottom - top) * line / 3;
                g.drawLine(left, y, left + chartWidth, y);
            }
            LocalDate today = LocalDate.now();
            LocalDate monday = today.with(DayOfWeek.MONDAY);
            int[] completed = new int[7];
            int[] scheduled = new int[7];
            for (Session session : sessions) {
                if (session.getDate().isBefore(monday) || session.getDate().isAfter(monday.plusDays(6))) continue;
                int index = session.getDate().getDayOfWeek().getValue() - 1;
                if (session.getStatus() == Session.Status.COMPLETED) completed[index]++;
                else if (session.getStatus() == Session.Status.SCHEDULED) scheduled[index]++;
            }
            for (GroupLearningSession session : groupSessions) {
                LocalDate date = session.getScheduledDate();
                if (date.isBefore(monday) || date.isAfter(monday.plusDays(6))) continue;
                int index = date.getDayOfWeek().getValue() - 1;
                if (session.getStatus() == GroupLearningSession.Status.COMPLETED) completed[index]++;
                else if (session.getStatus() == GroupLearningSession.Status.SCHEDULED) scheduled[index]++;
            }
            int max = 1;
            for (int i = 0; i < 7; i++) max = Math.max(max, completed[i] + scheduled[i]);
            int slot = Math.max(1, chartWidth / 7);
            int barWidth = Math.max(9, Math.min(20, slot / 2));
            for (int i = 0; i < 7; i++) {
                int x = left + i * slot + (slot - barWidth) / 2;
                int completedHeight = (bottom - top - 8) * completed[i] / max;
                int scheduledHeight = (bottom - top - 8) * scheduled[i] / max;
                g.setColor(new Color(0xC6B8FF));
                g.fillRoundRect(x, bottom - completedHeight - scheduledHeight,
                        barWidth, scheduledHeight, 7, 7);
                g.setColor(PRIMARY_PURPLE);
                g.fillRoundRect(x, bottom - completedHeight, barWidth,
                        Math.max(0, completedHeight), 7, 7);
                g.setColor(SECONDARY_TEXT);
                g.setFont(UI_FONT.deriveFont(9f));
                String label = DayOfWeek.of(i + 1).getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
                int labelWidth = g.getFontMetrics().stringWidth(label);
                g.drawString(label, x + (barWidth - labelWidth) / 2, getHeight() - 7);
            }
            g.dispose();
        }
    }

    private static final class CircularProgress extends JComponent {
        private final int percent;

        private CircularProgress(int percent) {
            this.percent = percent;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = Math.min(getWidth(), getHeight()) - 14;
            int x = (getWidth() - size) / 2, y = (getHeight() - size) / 2;
            g.setStroke(new java.awt.BasicStroke(10, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            g.setColor(SELECTED_NAV_BACKGROUND);
            g.drawArc(x, y, size, size, 90, -360);
            g.setColor(PRIMARY_PURPLE);
            g.drawArc(x, y, size, size, 90, -(int) (360 * percent / 100.0));
            String text = percent + "%";
            g.setFont(UI_FONT.deriveFont(Font.BOLD, 23f));
            g.setColor(PRIMARY_TEXT);
            int tx = (getWidth() - g.getFontMetrics().stringWidth(text)) / 2;
            int ty = getHeight() / 2 + g.getFontMetrics().getAscent() / 3;
            g.drawString(text, tx, ty);
            g.dispose();
        }
    }

    private static final class Avatar extends JComponent {
        private final String label;

        private Avatar(String name) {
            label = initials(name);
            setPreferredSize(new Dimension(46, 46));
            setMinimumSize(getPreferredSize());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SELECTED_NAV_BACKGROUND);
            g.fillOval(1, 1, getWidth() - 2, getHeight() - 2);
            g.setFont(UI_FONT.deriveFont(Font.BOLD, 13f));
            g.setColor(PRIMARY_PURPLE);
            int x = (getWidth() - g.getFontMetrics().stringWidth(label)) / 2;
            int y = (getHeight() + g.getFontMetrics().getAscent()) / 2 - 2;
            g.drawString(label, x, y);
            g.dispose();
        }
    }

    private static final class HeaderAvatar extends JComponent {
        private String label = "?";

        private HeaderAvatar() {
            setPreferredSize(new Dimension(36, 36));
            setMinimumSize(getPreferredSize());
        }

        private void setStudentName(String name) {
            label = initials(name);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SELECTED_NAV_BACKGROUND);
            g.fillOval(1, 1, getWidth() - 2, getHeight() - 2);
            g.setFont(UI_FONT.deriveFont(Font.BOLD, 11f));
            g.setColor(PRIMARY_PURPLE);
            int x = (getWidth() - g.getFontMetrics().stringWidth(label)) / 2;
            int y = (getHeight() + g.getFontMetrics().getAscent()) / 2 - 2;
            g.drawString(label, x, y);
            g.dispose();
        }
    }

    private static final class SidebarIcon implements javax.swing.Icon {
        private final int index;
        private final boolean selected;

        private SidebarIcon(int index, boolean selected) {
            this.index = index;
            this.selected = selected;
        }

        @Override public int getIconWidth() { return 18; }
        @Override public int getIconHeight() { return 18; }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(selected ? PRIMARY_PURPLE : SECONDARY_TEXT);
            g.setStroke(new java.awt.BasicStroke(1.7f, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            int variant = index % 6;
            if (variant == 0) {
                g.drawRoundRect(x + 2, y + 2, 14, 14, 4, 4);
                g.drawLine(x + 5, y + 7, x + 13, y + 7);
                g.drawLine(x + 5, y + 11, x + 11, y + 11);
            } else if (variant == 1) {
                g.drawOval(x + 5, y + 2, 8, 8);
                g.drawArc(x + 2, y + 8, 14, 10, 0, 180);
            } else if (variant == 2) {
                g.drawOval(x + 2, y + 2, 6, 6);
                g.drawOval(x + 10, y + 10, 6, 6);
                g.drawLine(x + 7, y + 7, x + 11, y + 11);
            } else if (variant == 3) {
                g.drawRoundRect(x + 2, y + 3, 14, 11, 3, 3);
                g.drawLine(x + 5, y + 7, x + 13, y + 7);
                g.drawLine(x + 5, y + 10, x + 10, y + 10);
            } else if (variant == 4) {
                g.drawLine(x + 4, y + 3, x + 4, y + 15);
                g.drawLine(x + 4, y + 15, x + 15, y + 15);
                g.drawLine(x + 6, y + 12, x + 9, y + 9);
                g.drawLine(x + 9, y + 9, x + 12, y + 11);
                g.drawLine(x + 12, y + 11, x + 15, y + 6);
            } else {
                g.drawOval(x + 3, y + 3, 12, 12);
                g.drawLine(x + 9, y + 5, x + 9, y + 9);
                g.drawLine(x + 9, y + 9, x + 12, y + 11);
            }
            g.dispose();
        }
    }

    private void styleComponentTree(Component component) {
        if (component instanceof JPanel panel && !(panel instanceof RoundedPanel)) {
            panel.setOpaque(false);
            if (panel.getBorder() instanceof javax.swing.border.TitledBorder titled) {
                panel.setBorder(new SectionCardBorder(titled.getTitle()));
            }
        } else if (component instanceof JScrollPane scrollPane) {
            boolean tableContainer = scrollPane.getViewport().getView() instanceof JTable;
            scrollPane.setOpaque(false);
            scrollPane.setBackground(CARD_BACKGROUND);
            scrollPane.setViewportBorder(null);
            scrollPane.setBorder(tableContainer ? new RoundedContainerBorder(14) : null);
            scrollPane.getViewport().setBackground(
                    tableContainer ? CARD_BACKGROUND : scrollPane.getBackground());
            styleScrollBar(scrollPane.getVerticalScrollBar(), true);
            styleScrollBar(scrollPane.getHorizontalScrollBar(), false);
        } else if (component instanceof JTable table) {
            table.setFont(UI_FONT.deriveFont(13.5f));
            table.setForeground(PRIMARY_TEXT);
            table.setBackground(Color.WHITE);
            table.setSelectionBackground(new Color(0xE5DEFF));
            table.setSelectionForeground(PRIMARY_TEXT);
            table.setGridColor(new Color(0xE7E5EF));
            table.setShowVerticalLines(false);
            table.setRowHeight(38);
            table.setFillsViewportHeight(true);
            table.setIntercellSpacing(new Dimension(0, 1));
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.setDefaultRenderer(Object.class, new SkilloraTableCellRenderer());
            table.setDefaultRenderer(Number.class, new SkilloraTableCellRenderer());
            if (table.getTableHeader() != null) {
                table.getTableHeader().setFont(UI_FONT.deriveFont(Font.BOLD, 13f));
                table.getTableHeader().setForeground(PRIMARY_TEXT);
                table.getTableHeader().setBackground(new Color(0xFAF9FD));
                table.getTableHeader().setPreferredSize(new Dimension(0, 40));
                table.getTableHeader().setReorderingAllowed(false);
                table.getTableHeader().setDefaultRenderer(new javax.swing.table.DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(JTable source, Object value,
                            boolean selected, boolean focused, int row, int column) {
                        JLabel header = (JLabel) super.getTableCellRendererComponent(
                                source, value, selected, focused, row, column);
                        header.setFont(UI_FONT.deriveFont(Font.BOLD, 13f));
                        header.setForeground(PRIMARY_TEXT);
                        header.setBackground(new Color(0xFAF9FD));
                        header.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(0, 0, 1, 0, LIGHT_BORDER),
                                new EmptyBorder(0, 10, 0, 10)));
                        header.setToolTipText(value == null ? null : value.toString());
                        return header;
                    }
                });
                for (int column = 0; column < table.getColumnModel().getColumnCount(); column++) {
                    var tableColumn = table.getColumnModel().getColumn(column);
                    int preferredWidth = Math.max(100,
                            table.getFontMetrics(table.getFont()).stringWidth(
                                    table.getColumnName(column)) + 36);
                    preferredWidth = Math.max(preferredWidth, preferredColumnWidth(table.getColumnName(column)));
                    for (int row = 0; row < table.getRowCount(); row++) {
                        Object value = table.getValueAt(row, column);
                        if (value != null) {
                            preferredWidth = Math.max(preferredWidth,
                                    Math.min(360, table.getFontMetrics(table.getFont())
                                            .stringWidth(value.toString()) + 28));
                        }
                    }
                    tableColumn.setPreferredWidth(Math.min(360, preferredWidth));
                }
            }
            installTableColumnSizer(table);
        } else if (component instanceof JButton button
                && !Boolean.TRUE.equals(button.getClientProperty("skillora.comboArrow"))) {
            styleButton(button, isPrimaryAction(button.getText()),
                    Boolean.TRUE.equals(button.getClientProperty("skillora.navigation")));
        } else if (component instanceof JTextField field
                && Boolean.TRUE.equals(field.getClientProperty("skillora.comboEditor"))) {
            field.setFont(UI_FONT.deriveFont(14f));
            field.setForeground(PRIMARY_TEXT);
            field.setBackground(Color.WHITE);
            field.setBorder(new EmptyBorder(7, 10, 7, 6));
        } else if (component instanceof JTextField field) {
            field.setFont(UI_FONT.deriveFont(14f));
            field.setForeground(field == globalSearchField
                    && "Search students, skills, groups".equals(field.getText())
                    ? MUTED_TEXT : PRIMARY_TEXT);
            field.setBackground(Color.WHITE);
            field.setBorder(new RoundedInputBorder());
            setInputSize(field, 200);
            installFocusBorder(field);
        } else if (component instanceof JComboBox<?> combo) {
            combo.setUI(new SkilloraComboBoxUI());
            combo.setFont(UI_FONT.deriveFont(13.5f));
            combo.setForeground(PRIMARY_TEXT);
            combo.setBackground(Color.WHITE);
            combo.setOpaque(false);
            combo.setBorder(new RoundedInputBorder());
            if (combo != headerStudentCombo) setInputSize(combo, 160);
            combo.setRenderer(new SkilloraComboRenderer());
            if (combo.isEditable()) {
                Component editorComponent = combo.getEditor().getEditorComponent();
                editorComponent.setFont(UI_FONT.deriveFont(14f));
                editorComponent.setForeground(PRIMARY_TEXT);
                editorComponent.setBackground(Color.WHITE);
                if (editorComponent instanceof JComponent editor) {
                    editor.putClientProperty("skillora.comboEditor", Boolean.TRUE);
                    editor.setBorder(new EmptyBorder(7, 10, 7, 6));
                }
            }
            combo.setMaximumRowCount(12);
            installFocusBorder(combo);
        } else if (component instanceof JTextArea area) {
            if (!Boolean.TRUE.equals(area.getClientProperty("skillora.plain"))) {
                area.setFont(UI_FONT.deriveFont(14f));
                area.setForeground(PRIMARY_TEXT);
                area.setBackground(Color.WHITE);
                area.setBorder(new RoundedInputBorder());
                installFocusBorder(area);
            }
        } else if (component instanceof JSpinner spinner) {
            spinner.setFont(UI_FONT.deriveFont(14f));
            spinner.setForeground(PRIMARY_TEXT);
            spinner.setBackground(Color.WHITE);
            spinner.setBorder(new RoundedInputBorder());
            setInputSize(spinner, 120);
            if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
                editor.getTextField().setFont(UI_FONT.deriveFont(14f));
                editor.getTextField().setForeground(PRIMARY_TEXT);
                editor.getTextField().setBackground(Color.WHITE);
                editor.getTextField().setBorder(new EmptyBorder(5, 8, 5, 8));
                installFocusBorder(spinner);
            }
        } else if (component instanceof JProgressBar progress) {
            progress.setForeground(PRIMARY_PURPLE);
            progress.setBackground(SELECTED_NAV_BACKGROUND);
            progress.setBorderPainted(false);
        } else if (component instanceof JLabel label) {
            if (Boolean.TRUE.equals(label.getClientProperty("skillora.formLabel"))) {
                label.setFont(UI_FONT.deriveFont(Font.BOLD, 14f));
                label.setForeground(PRIMARY_TEXT);
            } else if (label.getFont().getSize2D() <= 12f) {
                label.setFont(UI_FONT.deriveFont(13f));
                label.setForeground(MUTED_TEXT);
            }
        } else if (component instanceof JSplitPane splitPane) {
            splitPane.setBorder(null);
            splitPane.setBackground(APP_BACKGROUND);
            splitPane.setDividerSize(8);
        }

        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) styleComponentTree(child);
        }
    }

    private void setInputSize(JComponent input, int minimumWidth) {
        Dimension preferred = input.getPreferredSize();
        int width = Math.max(minimumWidth, preferred.width);
        input.setPreferredSize(new Dimension(width, 42));
        input.setMinimumSize(new Dimension(minimumWidth, 40));
    }

    private int preferredColumnWidth(String columnName) {
        String normalized = columnName.toLowerCase(Locale.ROOT);
        if (normalized.contains("description") || normalized.contains("message")
                || normalized.contains("feedback") || normalized.contains("matched skills")) return 240;
        if (normalized.contains("email") || normalized.contains("student name")
                || normalized.equals("name")) return 190;
        if (normalized.contains("student id") || normalized.contains("request id")
                || normalized.contains("session id") || normalized.contains("notification id")
                || normalized.contains("roadmap id") || normalized.contains("group id")) return 150;
        if (normalized.contains("skill") || normalized.contains("topic")) return 160;
        if (normalized.contains("date") || normalized.contains("time")) return 135;
        return 110;
    }

    private void installTableColumnSizer(JTable table) {
        if (Boolean.TRUE.equals(table.getClientProperty("skillora.columnSizerInstalled"))) return;
        table.putClientProperty("skillora.columnSizerInstalled", Boolean.TRUE);
        javax.swing.Timer resizeTimer = new javax.swing.Timer(100, event -> resizeTableColumns(table));
        resizeTimer.setRepeats(false);
        table.getModel().addTableModelListener(event -> resizeTimer.restart());
        resizeTimer.start();
    }

    private void resizeTableColumns(JTable table) {
        if (table.getColumnModel().getColumnCount() != table.getModel().getColumnCount()) return;
        for (int viewColumn = 0; viewColumn < table.getColumnModel().getColumnCount(); viewColumn++) {
            int modelColumn = table.convertColumnIndexToModel(viewColumn);
            String columnName = table.getModel().getColumnName(modelColumn);
            int width = Math.max(100, preferredColumnWidth(columnName));
            width = Math.max(width, table.getFontMetrics(table.getFont()).stringWidth(columnName) + 36);
            for (int row = 0; row < table.getModel().getRowCount(); row++) {
                Object value = table.getModel().getValueAt(row, modelColumn);
                if (value != null) {
                    width = Math.max(width, Math.min(360,
                            table.getFontMetrics(table.getFont()).stringWidth(value.toString()) + 28));
                }
            }
            table.getColumnModel().getColumn(viewColumn).setPreferredWidth(Math.min(360, width));
        }
    }

    private void installFocusBorder(JComponent component) {
        if (Boolean.TRUE.equals(component.getClientProperty("skillora.focusBorderInstalled"))) return;
        component.putClientProperty("skillora.focusBorderInstalled", Boolean.TRUE);
        component.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent event) {
                setFocusState(component, true);
                component.repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent event) {
                setFocusState(component, false);
                component.repaint();
            }
        });
    }

    private void setFocusState(JComponent component, boolean focused) {
        Component current = component;
        while (current instanceof JComponent swingComponent) {
            swingComponent.putClientProperty("skillora.focused", focused);
            swingComponent.repaint();
            current = swingComponent.getParent();
        }
    }

    private void styleScrollBar(javax.swing.JScrollBar scrollBar, boolean vertical) {
        scrollBar.setUnitIncrement(16);
        scrollBar.setBlockIncrement(64);
        scrollBar.setOpaque(false);
        scrollBar.setBorder(null);
        scrollBar.setPreferredSize(vertical ? new Dimension(10, 0) : new Dimension(0, 10));
        scrollBar.setUI(new SkilloraScrollBarUI(vertical));
    }

    private void styleButton(JButton button, boolean primary, boolean navigation) {
        button.putClientProperty("skillora.primary", primary);
        button.putClientProperty("skillora.navigation", navigation);
        button.setFont(UI_FONT.deriveFont(Font.BOLD, navigation ? 12.5f : 13f));
        Color defaultForeground = UIManager.getColor("Button.foreground");
        if (primary || PRIMARY_PURPLE.equals(button.getBackground())) {
            button.setForeground(Color.WHITE);
        } else if (button.getForeground() == null || button.getForeground().equals(defaultForeground)) {
            button.setForeground(PRIMARY_TEXT);
        }
        if (button.getBackground() == null || button.getBackground().equals(UIManager.getColor("Button.background"))) {
            button.setBackground(primary ? PRIMARY_PURPLE : Color.WHITE);
        }
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorder(navigation
                ? new EmptyBorder(10, 13, 10, 12)
                : new EmptyBorder(8, 14, 8, 14));
        if (!navigation) {
            Dimension preferred = button.getPreferredSize();
            boolean compact = button.getText() != null
                    && (button.getText().matches("\\d{1,2}") || button.getText().equals("‹")
                    || button.getText().equals("›"));
            button.setPreferredSize(compact
                    ? new Dimension(34, 32)
                    : new Dimension(Math.max(72, preferred.width), 40));
            button.setMinimumSize(compact ? new Dimension(30, 30) : new Dimension(68, 38));
        }
        button.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics graphics, JComponent component) {
                JButton current = (JButton) component;
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean isPrimary = Boolean.TRUE.equals(current.getClientProperty("skillora.primary"));
                boolean isNavigation = Boolean.TRUE.equals(current.getClientProperty("skillora.navigation"));
                Color fill = current.getBackground();
                if (!current.isEnabled()) fill = new Color(0xE9E7F0);
                else if (current.getModel().isPressed()) {
                    fill = isPrimary ? PRIMARY_PURPLE.darker() : new Color(0xE8E2FF);
                } else if (current.getModel().isRollover()) {
                    fill = isPrimary ? new Color(0x5D3DDA)
                            : (isNavigation ? SELECTED_NAV_BACKGROUND : new Color(0xF5F2FF));
                }
                if (fill == null) fill = isPrimary ? PRIMARY_PURPLE : Color.WHITE;
                g.setColor(fill);
                g.fillRoundRect(0, 0, current.getWidth(), current.getHeight(),
                        isNavigation ? 11 : 12, isNavigation ? 11 : 12);
                if (!isNavigation && !isPrimary) {
                    g.setColor(LIGHT_BORDER);
                    g.drawRoundRect(0, 0, current.getWidth() - 1, current.getHeight() - 1, 12, 12);
                }
                g.dispose();
                super.paint(graphics, component);
            }
        });
    }

    private boolean isPrimaryAction(String text) {
        if (text == null) return false;
        String normalized = text.toLowerCase(Locale.ROOT);
        return List.of("create", "save", "submit", "schedule", "add", "register",
                        "mark", "join", "accept", "update", "complete", "search offers")
                .stream().anyMatch(normalized::startsWith);
    }

    private static final class RoundedPanel extends JPanel {
        private final int radius;
        private final Color fillColor;
        private final Color accentColor;

        private RoundedPanel(int radius) {
            this(radius, CARD_BACKGROUND, null);
        }

        private RoundedPanel(int radius, Color fillColor, Color accentColor) {
            this.radius = radius;
            this.fillColor = fillColor;
            this.accentColor = accentColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fillColor);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            if (accentColor != null && getHeight() > 28) {
                g.setColor(accentColor);
                g.fillRoundRect(0, 14, 4, getHeight() - 28, 4, 4);
            }
            g.setColor(LIGHT_BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g.dispose();
        }
    }

    private BufferedImage createWindowLogo() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        new SkilloraLogoIcon(58).paintIcon(this, graphics, 3, 3);
        graphics.dispose();
        return image;
    }

    private static final class SkilloraLogoIcon implements Icon {
        private final int size;

        private SkilloraLogoIcon(int size) {
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SOFT_LAVENDER);
            g.fillRoundRect(x, y, size, size, Math.max(10, size / 3), Math.max(10, size / 3));
            int center = size / 2;
            int node = Math.max(7, size / 5);
            int inset = Math.max(8, size / 5);
            g.setStroke(new java.awt.BasicStroke(Math.max(2f, size / 15f),
                    java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g.setPaint(new java.awt.GradientPaint(x + inset, y + inset, PRIMARY_PURPLE,
                    x + size - inset, y + size - inset, ACCENT_BLUE));
            g.drawLine(x + center, y + center, x + inset, y + inset);
            g.drawLine(x + center, y + center, x + size - inset, y + inset);
            g.drawLine(x + center, y + center, x + size - inset, y + size - inset);
            g.setColor(Color.WHITE);
            g.fillOval(x + inset - node / 2, y + inset - node / 2, node, node);
            g.fillOval(x + size - inset - node / 2, y + inset - node / 2, node, node);
            g.fillOval(x + size - inset - node / 2, y + size - inset - node / 2, node, node);
            g.setColor(ACCENT_BLUE);
            g.fillOval(x + inset - node / 2, y + inset - node / 2, node, node);
            g.fillOval(x + size - inset - node / 2, y + inset - node / 2, node, node);
            g.fillOval(x + size - inset - node / 2, y + size - inset - node / 2, node, node);
            int hub = Math.max(9, size / 3);
            g.setColor(Color.WHITE);
            g.fillOval(x + center - hub / 2, y + center - hub / 2, hub, hub);
            g.setColor(PRIMARY_PURPLE);
            g.fillOval(x + center - hub / 2, y + center - hub / 2, hub, hub);
            g.dispose();
        }
    }

    private static final class RoundedContainerBorder implements Border {
        private final int radius;

        private RoundedContainerBorder(int radius) {
            this.radius = radius;
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(2, 2, 2, 2);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y,
                                int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(CARD_BACKGROUND);
            g.fillRoundRect(x, y, width - 1, height - 1, radius, radius);
            g.setColor(LIGHT_BORDER);
            g.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g.dispose();
        }
    }

    private static final class SectionCardBorder implements Border {
        private final String title;

        private SectionCardBorder(String title) {
            this.title = title;
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(46, 18, 18, 18);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(CARD_BACKGROUND);
            g.fillRoundRect(x, y, width - 1, height - 1, 18, 18);
            g.setColor(LIGHT_BORDER);
            g.drawRoundRect(x, y, width - 1, height - 1, 18, 18);
            g.setFont(UI_FONT.deriveFont(Font.BOLD, 17f));
            g.setColor(PRIMARY_TEXT);
            g.drawString(title, x + 19, y + 31);
            g.dispose();
        }
    }

    private static final class RoundedInputBorder implements Border {
        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(9, 11, 9, 11);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean focused = component instanceof JComponent swingComponent
                    && (swingComponent.hasFocus()
                    || Boolean.TRUE.equals(swingComponent.getClientProperty("skillora.focused")));
            g.setStroke(new java.awt.BasicStroke(focused ? 2f : 1f));
            g.setColor(focused ? PRIMARY_PURPLE : LIGHT_BORDER);
            g.drawRoundRect(x, y, width - 1, height - 1, 12, 12);
            g.dispose();
        }
    }

    private static final class SkilloraComboRenderer extends DefaultListCellRenderer {
        private boolean selectedPopupEntry;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, selected, cellHasFocus);
            selectedPopupEntry = selected && index >= 0;
            label.setFont(UI_FONT.deriveFont(selectedPopupEntry ? Font.BOLD : Font.PLAIN, 13.5f));
            label.setForeground(PRIMARY_TEXT);
            label.setBackground(Color.WHITE);
            label.setOpaque(false);
            label.setBorder(new EmptyBorder(7, 18, 7, 10));
            label.setToolTipText(value == null ? null : value.toString());
            return label;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (selectedPopupEntry) {
                g.setColor(SELECTED_NAV_BACKGROUND);
                g.fillRoundRect(2, 1, getWidth() - 4, getHeight() - 2, 10, 10);
                g.setColor(PRIMARY_PURPLE);
                g.fillRoundRect(7, Math.max(4, getHeight() / 2 - 7), 3, 14, 3, 3);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class SkilloraTableCellRenderer extends DefaultTableCellRenderer {
        private boolean statusBadge;
        private Color rowColor;
        private Color badgeColor;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            label.setFont(UI_FONT.deriveFont(13.5f));
            label.setForeground(PRIMARY_TEXT);
            label.setBorder(new EmptyBorder(0, 10, 0, 10));
            label.setToolTipText(value == null ? null : value.toString());
            String columnName = table.getColumnName(column).toLowerCase(Locale.ROOT);
            statusBadge = value != null && columnName.contains("status");
            rowColor = selected ? new Color(0xE5DEFF)
                    : table.convertRowIndexToModel(row) % 2 == 0 ? Color.WHITE : new Color(0xFAF9FD);
            badgeColor = statusBadge ? statusColor(value.toString()) : null;
            label.setOpaque(!statusBadge);
            if (selected) {
                label.setBackground(new Color(0xE5DEFF));
            } else {
                int modelRow = table.convertRowIndexToModel(row);
                if (modelRow < 0) {
                    label.setBackground(Color.WHITE);
                } else if (table.getModel().getColumnCount() > 0
                        && table.getModel().getValueAt(modelRow, 0) instanceof Integer rank
                        && table.getModel().getColumnName(0).equalsIgnoreCase("Rank")) {
                    label.setBackground(rank == 1 ? new Color(0xFFF7D6)
                            : rank == 2 ? new Color(0xF1F2F5)
                            : rank == 3 ? new Color(0xF8EBDD) : Color.WHITE);
                } else {
                    label.setBackground(modelRow % 2 == 0 ? Color.WHITE : new Color(0xFAF9FD));
                }
            }
            return label;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (statusBadge) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(rowColor);
                g.fillRect(0, 0, getWidth(), getHeight());
                if (!rowColor.equals(new Color(0xE5DEFF))) {
                    FontMetrics metrics = g.getFontMetrics(getFont());
                    int badgeWidth = Math.min(getWidth() - 10, metrics.stringWidth(getText()) + 26);
                    g.setColor(badgeColor);
                    g.fillRoundRect(5, 5, Math.max(0, badgeWidth), getHeight() - 10, 18, 18);
                }
                g.dispose();
            }
            super.paintComponent(graphics);
        }

        private Color statusColor(String status) {
            String normalized = status.toLowerCase(Locale.ROOT);
            if (normalized.contains("complete") || normalized.contains("accept")
                    || normalized.contains("active") || normalized.contains("joined")
                    || normalized.contains("read")) return SOFT_MINT;
            if (normalized.contains("schedule") || normalized.contains("pending")
                    || normalized.contains("upcoming")) return SOFT_PEACH;
            if (normalized.contains("progress")) return SOFT_BLUE;
            return SOFT_LAVENDER;
        }
    }

    private static final class SkilloraComboBoxUI extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            JButton arrow = new JButton() {
                @Override
                protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(comboBox != null && comboBox.hasFocus()
                            ? new Color(0xE8E2FF) : SOFT_LAVENDER);
                    g.fillRoundRect(1, 2, getWidth() - 3, getHeight() - 4, 10, 10);
                    g.setColor(PRIMARY_PURPLE);
                    g.setStroke(new java.awt.BasicStroke(2f,
                            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                    int centerX = getWidth() / 2;
                    int centerY = getHeight() / 2;
                    g.drawLine(centerX - 4, centerY - 1, centerX, centerY + 3);
                    g.drawLine(centerX, centerY + 3, centerX + 4, centerY - 1);
                    g.dispose();
                }
            };
            arrow.putClientProperty("skillora.comboArrow", Boolean.TRUE);
            arrow.setFocusable(false);
            arrow.setBorder(new EmptyBorder(0, 0, 0, 0));
            arrow.setContentAreaFilled(false);
            arrow.setOpaque(false);
            arrow.setPreferredSize(new Dimension(34, 36));
            return arrow;
        }

        @Override
        protected ComboPopup createPopup() {
            return new BasicComboPopup(comboBox) {
                {
                    setOpaque(false);
                    setLightWeightPopupEnabled(true);
                    setBorder(new RoundedPopupBorder());
                    JList<?> list = getList();
                    list.setCellRenderer(new SkilloraComboRenderer());
                    list.setBackground(Color.WHITE);
                    list.setSelectionBackground(SELECTED_NAV_BACKGROUND);
                    list.setSelectionForeground(PRIMARY_TEXT);
                    list.setFixedCellHeight(36);
                    for (Component child : getComponents()) {
                        if (child instanceof JScrollPane scrollPane) {
                            scrollPane.setOpaque(false);
                            scrollPane.setBorder(null);
                            scrollPane.getViewport().setOpaque(false);
                            stylePopupScrollBar(scrollPane.getVerticalScrollBar(), true);
                        }
                    }
                }

                @Override
                public void show(Component invoker, int x, int y) {
                    FontMetrics metrics = getList().getFontMetrics(getList().getFont());
                    int widestItem = comboBox.getWidth();
                    for (int index = 0; index < comboBox.getItemCount(); index++) {
                        Object item = comboBox.getItemAt(index);
                        if (item != null) {
                            widestItem = Math.max(widestItem,
                                    metrics.stringWidth(item.toString()) + 54);
                        }
                    }
                    int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width - 32;
                    Dimension preferred = getPreferredSize();
                    int popupWidth = Math.min(screenWidth, widestItem);
                    int popupHeight = Math.min(preferred.height,
                            Toolkit.getDefaultToolkit().getScreenSize().height - 48);
                    setPopupSize(new Dimension(popupWidth, Math.max(comboBox.getHeight(), popupHeight)));
                    super.show(invoker, x, y);
                }
            };
        }

        @Override
        public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean hasFocus) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, 12, 12);
            g.dispose();
        }

        private void stylePopupScrollBar(javax.swing.JScrollBar scrollBar, boolean vertical) {
            scrollBar.setOpaque(false);
            scrollBar.setBorder(null);
            scrollBar.setPreferredSize(vertical ? new Dimension(9, 0) : new Dimension(0, 9));
            scrollBar.setUI(new SkilloraScrollBarUI(vertical));
        }
    }

    private static final class RoundedPopupBorder implements Border {
        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(6, 6, 6, 6);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y,
                                int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRoundRect(x, y, width - 1, height - 1, 14, 14);
            g.setColor(LIGHT_BORDER);
            g.drawRoundRect(x, y, width - 1, height - 1, 14, 14);
            g.dispose();
        }
    }

    private static final class SkilloraScrollBarUI extends BasicScrollBarUI {
        private final boolean vertical;

        private SkilloraScrollBarUI(boolean vertical) {
            this.vertical = vertical;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createEmptyScrollButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createEmptyScrollButton();
        }

        private JButton createEmptyScrollButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setVisible(false);
            button.setFocusable(false);
            return button;
        }

        @Override
        public Dimension getPreferredSize(JComponent component) {
            return vertical ? new Dimension(10, super.getPreferredSize(component).height)
                    : new Dimension(super.getPreferredSize(component).width, 10);
        }

        @Override
        protected Dimension getMinimumThumbSize() {
            return vertical ? new Dimension(10, 26) : new Dimension(26, 10);
        }

        @Override
        protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xF0EEFA));
            int inset = 2;
            g.fillRoundRect(bounds.x + inset, bounds.y + inset,
                    Math.max(0, bounds.width - inset * 2), Math.max(0, bounds.height - inset * 2),
                    8, 8);
            g.dispose();
        }

        @Override
        protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
            if (!component.isEnabled() || bounds.width <= 0 || bounds.height <= 0) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(PRIMARY_PURPLE);
            int inset = 1;
            g.fillRoundRect(bounds.x + inset, bounds.y + inset,
                    Math.max(1, bounds.width - inset * 2), Math.max(1, bounds.height - inset * 2),
                    9, 9);
            g.dispose();
        }
    }

    private JPanel createProfilePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(createStudentFormPanel(), BorderLayout.NORTH);
        panel.add(new JScrollPane(studentTable), BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footer.add(profileStatusLabel);
        panel.add(footer, BorderLayout.SOUTH);

        studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        studentTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadSelectedStudent();
            }
        });
        return panel;
    }

    private JPanel createStudentFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Student Profile"));
        addFormRow(panel, 0, "Student ID:", idField);
        addFormRow(panel, 1, "Name:", nameField);
        addFormRow(panel, 2, "Email:", emailField);
        addFormRow(panel, 3, "Department:", departmentField);
        addFormRow(panel, 4, "Year of Study:", yearField);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton registerButton = new JButton("Register");
        JButton updateButton = new JButton("Update Selected");
        JButton deleteButton = new JButton("Delete Selected");
        JButton searchButton = new JButton("Search by ID");
        JButton showAllButton = new JButton("Show All");
        JButton clearButton = new JButton("Clear Form");

        registerButton.addActionListener(event -> registerStudent());
        updateButton.addActionListener(event -> updateStudent());
        deleteButton.addActionListener(event -> deleteStudent());
        searchButton.addActionListener(event -> searchStudent());
        showAllButton.addActionListener(event -> showAllStudents());
        clearButton.addActionListener(event -> clearForm());

        buttons.add(registerButton);
        buttons.add(updateButton);
        buttons.add(deleteButton);
        buttons.add(searchButton);
        buttons.add(showAllButton);
        buttons.add(clearButton);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 5;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(6, 4, 2, 4);
        panel.add(buttons, constraints);
        return panel;
    }

    private JPanel createSkillPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(
                "Add Skills, Search Offers, and Find Student Matches"));
        addFormRow(form, 0, "Student ID:", skillStudentIdField);
        addFormRow(form, 1, "Skill name (optional filter):", skillNameField);
        addFormRow(form, 2, "Category (optional filter):", categoryField);
        addFormRow(form, 3, "Add this skill as:", skillTypeCombo);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addSkillButton = new JButton("Add Skill");
        JButton searchButton = new JButton("Search Offers");
        JButton matchButton = new JButton("Find Matches");
        JButton clearButton = new JButton("Clear Skill Fields");
        addSkillButton.addActionListener(event -> addSkill());
        searchButton.addActionListener(event -> searchSkillOffers());
        matchButton.addActionListener(event -> findSkillMatches());
        clearButton.addActionListener(event -> clearSkillFields());
        buttons.add(addSkillButton);
        buttons.add(searchButton);
        buttons.add(matchButton);
        buttons.add(clearButton);

        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 0;
        buttonConstraints.gridy = 4;
        buttonConstraints.gridwidth = 2;
        buttonConstraints.anchor = GridBagConstraints.WEST;
        buttonConstraints.insets = new Insets(6, 4, 2, 4);
        form.add(buttons, buttonConstraints);

        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(skillTable), BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footer.add(skillStatusLabel);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createExchangePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Create an Exchange Request"));
        addFormRow(form, 0, "Requester student ID:", requesterIdField);
        addFormRow(form, 1, "Recipient student ID:", recipientIdField);
        addFormRow(form, 2, "Skill requester can teach:", offeredSkillField);
        addFormRow(form, 3, "Skill requested from recipient:", requestedSkillField);
        addFormRow(form, 4, "Recipient ID to respond:", respondingRecipientIdField);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton sendButton = new JButton("Send Request");
        JButton acceptButton = new JButton("Accept Selected");
        JButton rejectButton = new JButton("Reject Selected");
        JButton refreshButton = new JButton("Refresh Requests");
        sendButton.addActionListener(event -> sendExchangeRequest());
        acceptButton.addActionListener(event -> respondToSelectedRequest(true));
        rejectButton.addActionListener(event -> respondToSelectedRequest(false));
        refreshButton.addActionListener(event -> refreshRequestTable());
        buttons.add(sendButton);
        buttons.add(acceptButton);
        buttons.add(rejectButton);
        buttons.add(refreshButton);

        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 0;
        buttonConstraints.gridy = 5;
        buttonConstraints.gridwidth = 2;
        buttonConstraints.anchor = GridBagConstraints.WEST;
        buttonConstraints.insets = new Insets(6, 4, 2, 4);
        form.add(buttons, buttonConstraints);

        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(requestTable), BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footer.add(exchangeStatusLabel);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createSessionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filters.add(new JLabel("Status:"));
        filters.add(sessionStatusFilter);
        filters.add(new JLabel("Search student or skill:"));
        filters.add(sessionSearchField);
        JButton refreshButton = new JButton("Refresh Sessions");
        refreshButton.addActionListener(event -> {
            refreshSessionChoices();
            refreshSessionTable();
            showSessionStatus("Session list refreshed.");
        });
        filters.add(refreshButton);
        panel.add(filters, BorderLayout.NORTH);

        sessionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(sessionTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        JPanel scheduleForm = new JPanel(new GridBagLayout());
        scheduleForm.setBorder(BorderFactory.createTitledBorder("Schedule New Session"));
        addFormRow(scheduleForm, 0, "Teacher:", sessionTeacherCombo);
        addFormRow(scheduleForm, 1, "Learner:", sessionLearnerCombo);
        addFormRow(scheduleForm, 2, "Shared skill:", sessionSkillCombo);
        addFormRow(scheduleForm, 3, "Date (YYYY-MM-DD):", sessionDateField);
        addFormRow(scheduleForm, 4, "Time (HH:MM):", sessionTimeField);
        addFormRow(scheduleForm, 5, "Duration (minutes):", sessionDurationSpinner);
        JPanel scheduleActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton scheduleButton = new JButton("Schedule New Session");
        scheduleButton.addActionListener(event -> scheduleSession());
        scheduleActions.add(scheduleButton);
        GridBagConstraints scheduleButtonConstraints = new GridBagConstraints();
        scheduleButtonConstraints.gridx = 0;
        scheduleButtonConstraints.gridy = 6;
        scheduleButtonConstraints.gridwidth = 2;
        scheduleButtonConstraints.anchor = GridBagConstraints.WEST;
        scheduleButtonConstraints.insets = new Insets(5, 4, 2, 4);
        scheduleForm.add(scheduleActions, scheduleButtonConstraints);
        bottom.add(scheduleForm, BorderLayout.NORTH);

        JPanel sessionActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton detailsButton = new JButton("View Session Details");
        JButton rescheduleButton = new JButton("Reschedule Session");
        JButton completeButton = new JButton("Mark Session as Completed");
        JButton cancelButton = new JButton("Cancel Session");
        detailsButton.addActionListener(event -> viewSelectedSession());
        rescheduleButton.addActionListener(event -> rescheduleSelectedSession());
        completeButton.addActionListener(event -> changeSelectedSessionStatus(true));
        cancelButton.addActionListener(event -> changeSelectedSessionStatus(false));
        sessionActions.add(detailsButton);
        sessionActions.add(rescheduleButton);
        sessionActions.add(completeButton);
        sessionActions.add(cancelButton);
        bottom.add(sessionActions, BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footer.add(sessionStatusLabel);
        bottom.add(footer, BorderLayout.SOUTH);
        panel.add(bottom, BorderLayout.SOUTH);

        sessionStatusFilter.addActionListener(event -> refreshSessionTable());
        sessionSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshSessionTable();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshSessionTable();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshSessionTable();
            }
        });
        sessionTeacherCombo.addActionListener(event -> {
            if (!refreshingSessionChoices) updateSessionSkillChoices();
        });
        sessionLearnerCombo.addActionListener(event -> {
            if (!refreshingSessionChoices) updateSessionSkillChoices();
        });
        setStudentChoiceRenderer(sessionTeacherCombo);
        setStudentChoiceRenderer(sessionLearnerCombo);
        return panel;
    }

    private JPanel createNotificationsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBorder(BorderFactory.createTitledBorder("Notification Inbox"));
        addFormRow(controls, 0, "Student:", notificationRecipientCombo);
        addFormRow(controls, 1, "Filter:", notificationStatusFilter);
        addFormRow(controls, 2, "Search message:", notificationSearchField);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("Refresh Notifications");
        JButton markSelectedButton = new JButton("Mark Selected as Read");
        JButton markAllButton = new JButton("Mark All as Read");
        JButton detailsButton = new JButton("View Notification Details");
        refreshButton.addActionListener(event -> {
            refreshNotificationChoices();
            refreshNotificationTable();
            showNotificationStatus("Notifications refreshed.");
        });
        markSelectedButton.addActionListener(event -> markSelectedNotificationAsRead());
        markAllButton.addActionListener(event -> markVisibleNotificationsAsRead());
        detailsButton.addActionListener(event -> viewSelectedNotification());
        buttons.add(refreshButton);
        buttons.add(markSelectedButton);
        buttons.add(markAllButton);
        buttons.add(detailsButton);
        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 0;
        buttonConstraints.gridy = 3;
        buttonConstraints.gridwidth = 2;
        buttonConstraints.anchor = GridBagConstraints.WEST;
        buttonConstraints.insets = new Insets(5, 4, 3, 4);
        controls.add(buttons, buttonConstraints);
        panel.add(controls, BorderLayout.NORTH);

        notificationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        notificationTable.setAutoCreateRowSorter(true);
        panel.add(new JScrollPane(notificationTable), BorderLayout.CENTER);

        JPanel footer = new JPanel(new GridLayout(2, 1, 2, 2));
        footer.add(notificationUnreadLabel);
        footer.add(notificationStatusLabel);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
        panel.add(footer, BorderLayout.SOUTH);

        notificationRecipientCombo.addActionListener(event -> {
            if (!refreshingNotificationChoices) refreshNotificationTable();
        });
        notificationStatusFilter.addActionListener(event -> refreshNotificationTable());
        notificationSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshNotificationTable();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshNotificationTable();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshNotificationTable();
            }
        });
        notificationRecipientCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value == null ? "" : value.toString());
                return this;
            }
        });
        return panel;
    }

    private void refreshNotificationChoices() {
        NotificationRecipient selected = (NotificationRecipient) notificationRecipientCombo.getSelectedItem();
        String selectedId = selected == null ? "" : selected.studentId();
        List<NotificationRecipient> choices = new java.util.ArrayList<>();
        choices.add(new NotificationRecipient("", "All Students"));
        studentManager.getAllStudents().stream()
                .map(student -> new NotificationRecipient(student.getId(),
                        student.getName() + " (" + student.getId() + ")"))
                .forEach(choices::add);
        refreshingNotificationChoices = true;
        try {
            notificationRecipientCombo.setModel(
                    new DefaultComboBoxModel<>(choices.toArray(NotificationRecipient[]::new)));
            if (!selectedId.isBlank()) {
                for (int index = 0; index < notificationRecipientCombo.getItemCount(); index++) {
                    if (notificationRecipientCombo.getItemAt(index).studentId()
                            .equalsIgnoreCase(selectedId)) {
                        notificationRecipientCombo.setSelectedIndex(index);
                        break;
                    }
                }
            }
        } finally {
            refreshingNotificationChoices = false;
        }
    }

    private void refreshNotificationTable() {
        NotificationRecipient recipient = (NotificationRecipient) notificationRecipientCombo.getSelectedItem();
        String studentId = recipient == null ? "" : recipient.studentId();
        List<Notification> notifications = studentId.isBlank()
                ? notificationManager.getAllNotifications()
                : notificationManager.getNotificationsForStudent(studentId);
        int unreadCount = studentId.isBlank()
                ? (int) notifications.stream().filter(notification -> !notification.isRead()).count()
                : notificationManager.getUnreadCount(studentId);
        notificationUnreadLabel.setText("Unread notifications"
                + (recipient == null ? "" : " for " + recipient.displayName())
                + ": " + unreadCount);

        String filter = String.valueOf(notificationStatusFilter.getSelectedItem());
        String query = notificationSearchField.getText().trim().toLowerCase(Locale.ROOT);
        notificationTableModel.setRowCount(0);
        notifications.stream()
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed()
                        .thenComparing(Comparator.comparingInt(Notification::getNotificationId).reversed()))
                .filter(notification -> !"Unread Notifications".equals(filter) || !notification.isRead())
                .filter(notification -> !"Read Notifications".equals(filter) || notification.isRead())
                .filter(notification -> notification.getMessage().toLowerCase(Locale.ROOT).contains(query))
                .forEach(notification -> {
                    Student student = studentManager.findStudentById(notification.getRecipientId())
                            .orElse(null);
                    String studentName = student == null
                            ? notification.getRecipientId()
                            : student.getName() + " (" + student.getId() + ")";
                    notificationTableModel.addRow(new Object[]{
                            notification.getNotificationId(),
                            studentName,
                            notification.getEventType(),
                            notification.getMessage(),
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                    .withZone(ZoneId.systemDefault()).format(notification.getCreatedAt()),
                            notification.isRead() ? "Read" : "Unread"
                    });
                });
        if (notificationTableModel.getRowCount() == 0) {
            showNotificationStatus(notifications.isEmpty()
                    ? "No notifications are available for this selection."
                    : "No notifications match the current filter and search.");
        } else {
            showNotificationStatus(notificationTableModel.getRowCount() + " notification(s) shown.");
        }
    }

    private void markSelectedNotificationAsRead() {
        Notification notification = selectedNotification();
        if (notification == null) return;
        if (notification.isRead()) {
            showNotificationStatus("This notification is already marked as read.");
            return;
        }
        try {
            notificationManager.markAsRead(notification.getNotificationId(), notification.getRecipientId());
            boolean saved = saveData();
            refreshNotificationTable();
            showNotificationStatus("Notification #" + notification.getNotificationId() + " marked as read."
                    + (saved ? " Saved." : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
            showNotificationError(exception.getMessage());
        }
    }

    private void markVisibleNotificationsAsRead() {
        NotificationRecipient recipient = (NotificationRecipient) notificationRecipientCombo.getSelectedItem();
        String studentId = recipient == null ? "" : recipient.studentId();
        List<Notification> unread = (studentId.isBlank()
                ? notificationManager.getAllNotifications()
                : notificationManager.getNotificationsForStudent(studentId)).stream()
                .filter(notification -> !notification.isRead())
                .toList();
        if (unread.isEmpty()) {
            showNotificationStatus("There are no unread notifications to mark as read.");
            return;
        }
        try {
            unread.forEach(notification -> notificationManager.markAsRead(
                    notification.getNotificationId(), notification.getRecipientId()));
            boolean saved = saveData();
            refreshNotificationTable();
            showNotificationStatus(unread.size() + " notification(s) marked as read."
                    + (saved ? " Saved." : " The changes remain in memory and were not saved."));
        } catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
            showNotificationError(exception.getMessage());
        }
    }

    private void viewSelectedNotification() {
        Notification notification = selectedNotification();
        if (notification == null) return;
        Student recipient = studentManager.findStudentById(notification.getRecipientId()).orElse(null);
        String details = "Notification ID: " + notification.getNotificationId()
                + "\nRecipient: " + (recipient == null ? notification.getRecipientId()
                : recipient.getName() + " (" + recipient.getId() + ")")
                + "\nType: " + notification.getEventType()
                + "\nMessage: " + notification.getMessage()
                + "\nDate and time: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault()).format(notification.getCreatedAt())
                + "\nStatus: " + (notification.isRead() ? "Read" : "Unread");
        JOptionPane.showMessageDialog(this, details, "Notification Details", JOptionPane.INFORMATION_MESSAGE);
        showNotificationStatus("Viewing notification #" + notification.getNotificationId() + ".");
    }

    private Notification selectedNotification() {
        int selectedRow = notificationTable.getSelectedRow();
        if (selectedRow < 0) {
            showNotificationError("Select a notification from the table first.");
            return null;
        }
        int modelRow = notificationTable.convertRowIndexToModel(selectedRow);
        int notificationId = (Integer) notificationTableModel.getValueAt(modelRow, 0);
        return notificationManager.getAllNotifications().stream()
                .filter(notification -> notification.getNotificationId() == notificationId)
                .findFirst()
                .orElseGet(() -> {
                    showNotificationError("The selected notification no longer exists.");
                    refreshNotificationTable();
                    return null;
                });
    }

    private void createNotificationIfAbsent(String recipientId, String eventType, String message) {
        notificationManager.createNotificationIfAbsent(recipientId, eventType, message);
    }

    private void refreshNotificationsAfterOperation() {
        refreshNotificationChoices();
        refreshNotificationTable();
    }

    private void showNotificationError(String message) {
        JOptionPane.showMessageDialog(this, message, "Notifications", JOptionPane.WARNING_MESSAGE);
        showNotificationStatus(message);
    }

    private void showNotificationStatus(String message) {
        notificationStatusLabel.setText(message);
    }

    private JPanel createRatingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Rate a Completed Session"));
        addFormRow(form, 0, "Your student profile:", ratingStudentCombo);
        addFormRow(form, 1, "Completed session:", ratingSessionCombo);
        addFormRow(form, 2, "Rating (1–5 stars):", ratingScoreSpinner);
        ratingFeedbackArea.setLineWrap(true);
        ratingFeedbackArea.setWrapStyleWord(true);
        addFormRow(form, 3, "Written feedback (max 1,000 characters):",
                new JScrollPane(ratingFeedbackArea));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton submitButton = new JButton("Submit Rating");
        JButton refreshButton = new JButton("Refresh Ratings");
        submitButton.addActionListener(event -> submitRating());
        refreshButton.addActionListener(event -> {
            refreshRatingStudentChoices();
            refreshRatingViews();
            showRatingStatus("Ratings refreshed.");
        });
        actions.add(submitButton);
        actions.add(refreshButton);
        GridBagConstraints actionConstraints = new GridBagConstraints();
        actionConstraints.gridx = 0;
        actionConstraints.gridy = 4;
        actionConstraints.gridwidth = 2;
        actionConstraints.anchor = GridBagConstraints.WEST;
        actionConstraints.insets = new Insets(5, 4, 3, 4);
        form.add(actions, actionConstraints);
        panel.add(form, BorderLayout.NORTH);

        JPanel receivedPanel = new JPanel(new BorderLayout(6, 6));
        receivedPanel.setBorder(BorderFactory.createTitledBorder("Ratings and Feedback Received"));
        receivedPanel.add(ratingAverageLabel, BorderLayout.NORTH);
        receivedRatingTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        receivedPanel.add(new JScrollPane(receivedRatingTable), BorderLayout.CENTER);
        panel.add(receivedPanel, BorderLayout.CENTER);
        panel.add(ratingStatusLabel, BorderLayout.SOUTH);

        ratingStudentCombo.addActionListener(event -> refreshRatingViews());
        ratingSessionCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value == null ? "" : value.toString());
                return this;
            }
        });
        setStudentChoiceRenderer(ratingStudentCombo);
        return panel;
    }

    private void refreshRatingStudentChoices() {
        String studentId = selectedStudentId(ratingStudentCombo);
        List<StudentChoice> choices = studentManager.getAllStudents().stream()
                .map(StudentChoice::new)
                .toList();
        ratingStudentCombo.setModel(new DefaultComboBoxModel<>(choices.toArray(StudentChoice[]::new)));
        selectStudentChoice(ratingStudentCombo, studentId);
    }

    private void refreshRatingViews() {
        StudentChoice selected = (StudentChoice) ratingStudentCombo.getSelectedItem();
        refreshEligibleRatingSessions(selected == null ? "" : selected.student().getId());
        receivedRatingTableModel.setRowCount(0);
        if (selected == null) {
            ratingAverageLabel.setText("Average rating: No ratings yet");
            return;
        }
        PortfolioManager.Portfolio portfolio = portfolioManager.getPortfolio(selected.student().getId());
        ratingAverageLabel.setText(formatAverageRating(portfolio.averageRating(), portfolio.ratingCount()));
        for (PortfolioManager.ReceivedRating rating : portfolio.receivedRatings()) {
            receivedRatingTableModel.addRow(new Object[]{
                    rating.sessionId(),
                    rating.skillName(),
                    rating.reviewer().name() + " (" + rating.reviewer().id() + ")",
                    rating.score() + " / 5",
                    rating.feedback(),
                    formatTimestamp(rating.createdAt())
            });
        }
        if (portfolio.receivedRatings().isEmpty()) {
            showRatingStatus("No ratings have been received by " + selected.student().getName() + " yet.");
        }
    }

    private void refreshEligibleRatingSessions(String reviewerId) {
        Integer previousSessionId = ratingSessionCombo.getSelectedItem() instanceof CompletedSessionChoice choice
                ? choice.session().getSessionId() : null;
        List<CompletedSessionChoice> choices = reviewerId == null || reviewerId.isBlank()
                ? List.of()
                : sessionManager.getAllSessions().stream()
                .filter(session -> session.getStatus() == Session.Status.COMPLETED)
                .filter(session -> session.getTeacher().getId().equalsIgnoreCase(reviewerId)
                        || session.getLearner().getId().equalsIgnoreCase(reviewerId))
                .filter(session -> ratingManager.getAllRatings().stream().noneMatch(rating ->
                        rating.getSessionId() == session.getSessionId()
                                && rating.getReviewer().getId().equalsIgnoreCase(reviewerId)))
                .map(CompletedSessionChoice::new)
                .toList();
        ratingSessionCombo.setModel(new DefaultComboBoxModel<>(choices.toArray(CompletedSessionChoice[]::new)));
        if (previousSessionId != null) {
            for (int index = 0; index < ratingSessionCombo.getItemCount(); index++) {
                if (ratingSessionCombo.getItemAt(index).session().getSessionId() == previousSessionId) {
                    ratingSessionCombo.setSelectedIndex(index);
                    break;
                }
            }
        }
    }

    private void submitRating() {
        StudentChoice reviewer = (StudentChoice) ratingStudentCombo.getSelectedItem();
        CompletedSessionChoice choice = (CompletedSessionChoice) ratingSessionCombo.getSelectedItem();
        if (reviewer == null) {
            showRatingError("Select your student profile before submitting a rating.");
            return;
        }
        if (choice == null) {
            showRatingError("Select an eligible completed session to rate.");
            return;
        }
        String feedback = ratingFeedbackArea.getText();
        if (feedback == null || feedback.isBlank()) {
            showRatingError("Written feedback is required.");
            return;
        }
        if (feedback.trim().length() > 1000) {
            showRatingError("Feedback must be 1,000 characters or fewer.");
            return;
        }

        int sessionId = choice.session().getSessionId();
        String reviewerId = reviewer.student().getId();
        try {
            Rating rating = ratingManager.submitRating(sessionId, reviewerId,
                    (Integer) ratingScoreSpinner.getValue(), feedback);
            if (!saveData()) {
                ratingManager.discardRating(sessionId, reviewerId);
                refreshRatingViews();
                refreshPortfolioView();
                return;
            }
            ratingFeedbackArea.setText("");
            refreshRatingViews();
            refreshPortfolioView();
            refreshEligibleRatingSessions(reviewerId);
            showRatingStatus("Your " + rating.getScore() + "-star rating for session #"
                    + sessionId + " was saved.");
        } catch (IllegalArgumentException | IllegalStateException
                 | java.util.NoSuchElementException exception) {
            showRatingError(exception.getMessage());
        }
    }

    private JPanel createPortfolioPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel header = new JPanel(new GridBagLayout());
        header.setBorder(BorderFactory.createTitledBorder("Student Portfolio"));
        addFormRow(header, 0, "Student:", portfolioStudentCombo);
        JButton refreshButton = new JButton("Refresh Portfolio");
        refreshButton.addActionListener(event -> {
            refreshPortfolioStudentChoices();
            refreshPortfolioView();
            showPortfolioStatus("Portfolio refreshed.");
        });
        GridBagConstraints refreshConstraints = new GridBagConstraints();
        refreshConstraints.gridx = 2;
        refreshConstraints.gridy = 0;
        refreshConstraints.anchor = GridBagConstraints.WEST;
        refreshConstraints.insets = new Insets(3, 4, 3, 8);
        header.add(refreshButton, refreshConstraints);

        JPanel profile = new JPanel(new GridLayout(5, 1, 2, 2));
        profile.add(portfolioNameLabel);
        profile.add(portfolioProfileLabel);
        profile.add(portfolioGroupsLabel);
        profile.add(portfolioAverageLabel);
        profile.add(portfolioRewardsLabel);
        JPanel summary = new JPanel(new BorderLayout(4, 4));
        summary.add(header, BorderLayout.NORTH);
        summary.add(profile, BorderLayout.CENTER);
        summary.add(new JScrollPane(portfolioSkillTable), BorderLayout.SOUTH);
        panel.add(summary, BorderLayout.NORTH);

        JPanel portfolioDetails = createSegmentedCards(
                new String[]{"Completed Sessions", "Received Ratings", "Achievements & Badges"},
                new Component[]{new JScrollPane(portfolioSessionTable), new JScrollPane(portfolioRatingTable),
                        new JScrollPane(portfolioAchievementTable)});
        panel.add(portfolioDetails, BorderLayout.CENTER);
        panel.add(portfolioStatusLabel, BorderLayout.SOUTH);

        portfolioStudentCombo.addActionListener(event -> refreshPortfolioView());
        setStudentChoiceRenderer(portfolioStudentCombo);
        return panel;
    }

    private void refreshPortfolioStudentChoices() {
        String studentId = selectedStudentId(portfolioStudentCombo);
        List<StudentChoice> choices = studentManager.getAllStudents().stream()
                .map(StudentChoice::new)
                .toList();
        portfolioStudentCombo.setModel(new DefaultComboBoxModel<>(choices.toArray(StudentChoice[]::new)));
        selectStudentChoice(portfolioStudentCombo, studentId);
    }

    private void refreshPortfolioView() {
        portfolioSessionTableModel.setRowCount(0);
        portfolioRatingTableModel.setRowCount(0);
        portfolioAchievementTableModel.setRowCount(0);
        portfolioSkillTableModel.setRowCount(0);
        StudentChoice choice = (StudentChoice) portfolioStudentCombo.getSelectedItem();
        if (choice == null) {
            portfolioNameLabel.setText("No student selected");
            portfolioProfileLabel.setText("No student profile is available.");
            portfolioGroupsLabel.setText("Learning groups: None");
            portfolioAverageLabel.setText("Average rating: No ratings yet");
            portfolioRewardsLabel.setText("Reward points: 0");
            portfolioStatusLabel.setText("Register a student to create a portfolio.");
            return;
        }

        Student student = choice.student();
        PortfolioManager.Portfolio portfolio = portfolioManager.getPortfolio(student.getId());
        portfolioNameLabel.setText(portfolio.name() + " (" + portfolio.studentId() + ")");
        List<LearningGroup> joinedGroups = groupLearningManager.getGroupsForStudent(student.getId());
        portfolioProfileLabel.setText(student.getEmail() + "  |  " + portfolio.department()
                + "  |  Year " + portfolio.yearOfStudy());
        portfolioGroupsLabel.setText("Learning groups (" + joinedGroups.size() + "): "
                + (joinedGroups.isEmpty() ? "None" : joinedGroups.stream()
                .map(LearningGroup::getName).collect(Collectors.joining(", "))));
        portfolioAverageLabel.setText(formatAverageRating(portfolio.averageRating(), portfolio.ratingCount()));
        portfolioRewardsLabel.setText("Reward points: " + rewardManager.getTotalPoints(student.getId()));
        List<String> skillsToTeach = portfolio.skillsToTeach().stream()
                .map(skill -> skill.getName() + " (" + skill.getCategory() + ")").toList();
        List<String> skillsToLearn = portfolio.skillsToLearn().stream()
                .map(skill -> skill.getName() + " (" + skill.getCategory() + ")").toList();
        int skillRows = Math.max(1, Math.max(skillsToTeach.size(), skillsToLearn.size()));
        for (int index = 0; index < skillRows; index++) {
            portfolioSkillTableModel.addRow(new Object[]{
                    index < skillsToTeach.size() ? skillsToTeach.get(index) : "",
                    index < skillsToLearn.size() ? skillsToLearn.get(index) : ""
            });
        }

        for (PortfolioManager.CompletedSession session : portfolio.completedSessions()) {
            portfolioSessionTableModel.addRow(new Object[]{
                    session.sessionId(), session.role(),
                    session.otherParticipant().name() + " (" + session.otherParticipant().id() + ")",
                    session.skill().getName(), session.date(), session.startTime(),
                    session.durationMinutes() + " minutes"
            });
        }
        for (PortfolioManager.ReceivedRating rating : portfolio.receivedRatings()) {
            portfolioRatingTableModel.addRow(new Object[]{
                    rating.sessionId(), rating.skillName(),
                    rating.reviewer().name() + " (" + rating.reviewer().id() + ")",
                    rating.score() + " / 5", rating.feedback(), formatTimestamp(rating.createdAt())
            });
        }
        List<model.AchievementBadge> earnedBadges = rewardManager.getBadgesForStudent(student.getId());
        Set<String> displayedBadgeIds = new HashSet<>();
        for (PortfolioManager.Achievement achievement : portfolio.achievements()) {
            String badgeId = switch (achievement.milestone()) {
                case 1 -> "FIRST_SESSION";
                case 5 -> "ACTIVE_LEARNER";
                case 10 -> "SKILL_EXPLORER";
                default -> "";
            };
            model.AchievementBadge badge = earnedBadges.stream()
                    .filter(item -> item.getBadgeId().equals(badgeId)).findFirst().orElse(null);
            RewardManager.BadgeDefinition definition = rewardManager.getBadgeDefinitions().stream()
                    .filter(item -> item.id().equals(badgeId)).findFirst().orElse(null);
            portfolioAchievementTableModel.addRow(new Object[]{
                    badge == null ? achievement.title() : badge.getBadgeName(),
                    definition == null ? achievement.description() : definition.description(),
                    achievement.milestone(),
                    badge == null ? "—" : badge.getDateEarned().toString()
            });
            if (badge != null) displayedBadgeIds.add(badge.getBadgeId());
        }
        for (model.AchievementBadge badge : earnedBadges) {
            if (displayedBadgeIds.contains(badge.getBadgeId())) continue;
            RewardManager.BadgeDefinition definition = rewardManager.getBadgeDefinitions().stream()
                    .filter(item -> item.id().equals(badge.getBadgeId())).findFirst().orElseThrow();
            portfolioAchievementTableModel.addRow(new Object[]{
                    badge.getBadgeName(), definition.description(), "Achievement badge",
                    badge.getDateEarned().toString()
            });
        }
        if (portfolio.completedSessions().isEmpty()) {
            portfolioSessionTableModel.addRow(new Object[]{"—", "—", "No completed sessions yet", "—", "—", "—", "—"});
        }
        if (portfolio.receivedRatings().isEmpty()) {
            portfolioRatingTableModel.addRow(new Object[]{"—", "—", "No ratings received yet", "—", "—", "—"});
        }
        if (portfolio.achievements().isEmpty() && earnedBadges.isEmpty()) {
            portfolioAchievementTableModel.addRow(new Object[]{
                    "No achievements earned yet", "Complete sessions or roadmap milestones to unlock achievements.",
                    "—", "—"
            });
        }
        portfolioStatusLabel.setText(portfolio.completedSessions().size() + " completed session(s), "
                + portfolio.ratingCount() + " received rating(s), "
                + portfolio.achievements().size() + " session milestone(s), "
                + earnedBadges.size() + " reward badge(s).");
    }

    private static String formatAverageRating(double average, int count) {
        return count == 0 ? "Average rating: No ratings yet"
                : String.format(Locale.ROOT, "Average rating: %.2f / 5 (%d rating%s)",
                average, count, count == 1 ? "" : "s");
    }

    private static String formatTimestamp(String value) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault()).format(java.time.Instant.parse(value));
    }

    private void showRatingError(String message) {
        JOptionPane.showMessageDialog(this, message, "Ratings & Feedback", JOptionPane.WARNING_MESSAGE);
        showRatingStatus(message);
    }

    private void showRatingStatus(String message) {
        ratingStatusLabel.setText(message);
    }

    private void showPortfolioStatus(String message) {
        portfolioStatusLabel.setText(message);
    }

    private JPanel createRewardsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel header = new JPanel(new GridBagLayout());
        header.setBorder(BorderFactory.createTitledBorder("Rewards & Gamification"));
        addFormRow(header, 0, "Student:", rewardStudentCombo);
        GridBagConstraints pointsConstraints = new GridBagConstraints();
        pointsConstraints.gridx = 2;
        pointsConstraints.gridy = 0;
        pointsConstraints.anchor = GridBagConstraints.WEST;
        pointsConstraints.insets = new Insets(3, 10, 3, 8);
        header.add(rewardPointsLabel, pointsConstraints);
        JButton refreshButton = new JButton("Refresh Rewards");
        refreshButton.addActionListener(event -> synchronizeRewardsAndRefresh());
        GridBagConstraints refreshConstraints = new GridBagConstraints();
        refreshConstraints.gridx = 3;
        refreshConstraints.gridy = 0;
        refreshConstraints.anchor = GridBagConstraints.WEST;
        refreshConstraints.insets = new Insets(3, 4, 3, 8);
        header.add(refreshButton, refreshConstraints);

        JPanel progressPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        progressPanel.setBorder(BorderFactory.createTitledBorder("Progress to Achievements"));
        rewardSessionProgressBar.setStringPainted(true);
        rewardRoadmapProgressBar.setStringPainted(true);
        progressPanel.add(rewardSessionProgressBar);
        progressPanel.add(rewardRoadmapProgressBar);
        JPanel top = new JPanel(new BorderLayout(5, 5));
        top.add(header, BorderLayout.NORTH);
        top.add(progressPanel, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);

        earnedBadgeTable.setAutoCreateRowSorter(true);
        lockedBadgeTable.setAutoCreateRowSorter(true);
        JPanel badgeTabs = createSegmentedCards(
                new String[]{"Earned Badges", "Locked Badges"},
                new Component[]{new JScrollPane(earnedBadgeTable), new JScrollPane(lockedBadgeTable)});

        JPanel historyPanel = new JPanel(new BorderLayout(4, 4));
        historyPanel.setBorder(BorderFactory.createTitledBorder("Reward History"));
        rewardHistoryTable.setAutoCreateRowSorter(true);
        historyPanel.add(new JScrollPane(rewardHistoryTable), BorderLayout.CENTER);
        JSplitPane left = new JSplitPane(JSplitPane.VERTICAL_SPLIT, badgeTabs, historyPanel);
        left.setResizeWeight(0.52);
        left.setDividerLocation(230);

        JPanel leaderboardPanel = new JPanel(new BorderLayout(4, 4));
        leaderboardPanel.setBorder(BorderFactory.createTitledBorder("Student Leaderboard"));
        leaderboardTable.setAutoCreateRowSorter(true);
        leaderboardTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                          boolean focused, int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        table, value, selected, focused, row, column);
                int modelRow = table.convertRowIndexToModel(row);
                Object rankValue = leaderboardTableModel.getValueAt(modelRow, 0);
                int rank = rankValue instanceof Integer integerRank ? integerRank : Integer.MAX_VALUE;
                if (!selected) {
                    component.setBackground(rank == 1 ? new Color(255, 244, 190)
                            : rank == 2 ? new Color(232, 236, 242)
                            : rank == 3 ? new Color(245, 225, 207) : table.getBackground());
                }
                return component;
            }
        });
        leaderboardPanel.add(new JScrollPane(leaderboardTable), BorderLayout.CENTER);
        JSplitPane content = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, leaderboardPanel);
        content.setResizeWeight(0.58);
        content.setDividerLocation(600);
        panel.add(content, BorderLayout.CENTER);
        panel.add(rewardStatusLabel, BorderLayout.SOUTH);

        rewardStudentCombo.addActionListener(event -> {
            if (!refreshingRewardChoices) refreshRewardsView();
        });
        setStudentChoiceRenderer(rewardStudentCombo);
        return panel;
    }

    private void refreshRewardStudentChoices() {
        String studentId = selectedStudentId(rewardStudentCombo);
        List<StudentChoice> choices = studentManager.getAllStudents().stream()
                .map(StudentChoice::new)
                .toList();
        refreshingRewardChoices = true;
        try {
            rewardStudentCombo.setModel(new DefaultComboBoxModel<>(choices.toArray(StudentChoice[]::new)));
            selectStudentChoice(rewardStudentCombo, studentId);
        } finally {
            refreshingRewardChoices = false;
        }
    }

    private void refreshRewardsView() {
        earnedBadgeTableModel.setRowCount(0);
        lockedBadgeTableModel.setRowCount(0);
        rewardHistoryTableModel.setRowCount(0);
        leaderboardTableModel.setRowCount(0);
        StudentChoice choice = (StudentChoice) rewardStudentCombo.getSelectedItem();
        for (RewardManager.LeaderboardEntry entry : rewardManager.getLeaderboard()) {
            leaderboardTableModel.addRow(new Object[]{entry.rank(), entry.student().getName()
                    + " (" + entry.student().getId() + ")", entry.totalPoints(),
                    entry.completedSessions(), entry.completedMilestones(), entry.earnedBadges()});
        }
        if (leaderboardTableModel.getRowCount() == 0) {
            leaderboardTableModel.addRow(new Object[]{"—", "No students registered", 0, 0, 0, 0});
        }

        if (choice == null) {
            rewardPointsLabel.setText("Total points: 0");
            rewardSessionProgressBar.setMaximum(1);
            rewardSessionProgressBar.setValue(0);
            rewardSessionProgressBar.setString("Complete a session to begin");
            rewardRoadmapProgressBar.setValue(0);
            rewardRoadmapProgressBar.setString("Complete a roadmap milestone to begin");
            for (RewardManager.BadgeDefinition definition : rewardManager.getBadgeDefinitions())
                lockedBadgeTableModel.addRow(new Object[]{definition.name(), definition.description(), "Select a student"});
            rewardHistoryTableModel.addRow(new Object[]{"—", "—", "No student selected", "—", "—"});
            rewardStatusLabel.setText("Register a student to view reward points, badges, and history.");
            return;
        }

        Student student = choice.student();
        rewardPointsLabel.setText("Total points: " + rewardManager.getTotalPoints(student.getId()));
        RewardManager.ProgressSummary progress = rewardManager.getProgressSummary(student.getId());
        rewardSessionProgressBar.setMaximum(progress.nextSessionThreshold());
        rewardSessionProgressBar.setValue(Math.min(progress.completedSessions(), progress.nextSessionThreshold()));
        rewardSessionProgressBar.setString(progress.completedSessions() >= 10
                ? "Session badges complete: 10 / 10"
                : progress.completedSessions() + " / " + progress.nextSessionThreshold()
                + " sessions toward the next badge");
        rewardRoadmapProgressBar.setValue(progress.bestRoadmapProgress());
        rewardRoadmapProgressBar.setString(progress.completedRoadmaps() > 0
                ? "Roadmap Champion earned"
                : progress.bestRoadmapProgress() + "% toward completing a roadmap");

        List<model.AchievementBadge> earned = rewardManager.getBadgesForStudent(student.getId());
        Set<String> earnedIds = earned.stream()
                .map(badge -> badge.getBadgeId().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        for (RewardManager.BadgeDefinition definition : rewardManager.getBadgeDefinitions()) {
            model.AchievementBadge badge = earned.stream()
                    .filter(item -> item.getBadgeId().equalsIgnoreCase(definition.id())).findFirst().orElse(null);
            if (badge == null) {
                lockedBadgeTableModel.addRow(new Object[]{definition.name(), definition.description(),
                        rewardBadgeProgress(definition.id(), progress)});
            } else {
                earnedBadgeTableModel.addRow(new Object[]{badge.getBadgeName(), definition.description(),
                        badge.getDateEarned().toString()});
            }
        }
        if (earnedBadgeTableModel.getRowCount() == 0)
            earnedBadgeTableModel.addRow(new Object[]{"—", "No badges earned yet", "—"});
        if (lockedBadgeTableModel.getRowCount() == 0)
            lockedBadgeTableModel.addRow(new Object[]{"—", "All achievement badges earned", "—"});

        for (model.RewardTransaction transaction : rewardManager.getTransactionsForStudent(student.getId())) {
            rewardHistoryTableModel.addRow(new Object[]{transaction.getTransactionId(),
                    student.getName() + " (" + student.getId() + ")",
                    transaction.getActivityDescription(), transaction.getPointsEarned(),
                    formatTimestamp(transaction.getCreatedAt().toString())});
        }
        if (rewardHistoryTableModel.getRowCount() == 0)
            rewardHistoryTableModel.addRow(new Object[]{"—", student.getName(), "No reward history yet", 0, "—"});
        rewardStatusLabel.setText(earned.size() + " badge(s) earned; "
                + rewardHistoryTableModel.getRowCount() + " reward history row(s) shown.");
        if (earnedIds.size() == rewardManager.getBadgeDefinitions().size())
            rewardStatusLabel.setText("All achievement badges earned. "
                    + rewardHistoryTableModel.getRowCount() + " reward history row(s) shown.");
    }

    private static String rewardBadgeProgress(String badgeId, RewardManager.ProgressSummary progress) {
        return switch (badgeId) {
            case "FIRST_SESSION" -> Math.min(progress.completedSessions(), 1) + " / 1 completed session";
            case "ACTIVE_LEARNER" -> Math.min(progress.completedSessions(), 5) + " / 5 completed sessions";
            case "SKILL_EXPLORER" -> Math.min(progress.completedSessions(), 10) + " / 10 completed sessions";
            case "FIRST_MILESTONE" -> (progress.completedMilestones() > 0 ? 1 : 0) + " / 1 completed milestone";
            case "ROADMAP_CHAMPION" -> progress.bestRoadmapProgress() + "% roadmap progress";
            default -> "Locked";
        };
    }

    private void synchronizeRewardsAndRefresh() {
        RewardManager.RewardState before = rewardManager.snapshot();
        rewardManager.synchronizeCompletedActivities();
        boolean changed = rewardManager.getAllTransactions().size() != before.transactions().size()
                || rewardManager.getAllBadges().size() != before.badges().size();
        boolean saved = !changed || saveData();
        if (!saved) rewardManager.restoreState(before);
        refreshRewardsView();
        refreshPortfolioView();
        showRewardStatus(changed ? (saved ? "Newly eligible rewards were awarded and saved."
                : "Reward changes were not applied because saving failed.")
                : "Rewards and leaderboard refreshed.");
    }

    private void showRewardStatus(String message) {
        rewardStatusLabel.setText(message);
    }

    private JPanel createGroupLearningPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBorder(BorderFactory.createTitledBorder("Discover and Create Learning Circles"));
        addFormRow(controls, 0, "Acting student:", groupActingStudentCombo);
        addFormRow(controls, 1, "Search group name:", groupNameSearchField);
        addFormRow(controls, 2, "Search skill:", groupSkillSearchField);
        addFormRow(controls, 3, "Group status:", groupStatusFilter);

        JPanel createForm = new JPanel(new GridBagLayout());
        createForm.setBorder(BorderFactory.createTitledBorder("Create Group"));
        addFormRow(createForm, 0, "Group name:", groupNameField);
        addFormRow(createForm, 1, "Learning topic:", groupTopicField);
        addFormRow(createForm, 2, "Description:", new JScrollPane(groupDescriptionArea));
        addFormRow(createForm, 3, "Maximum members:", groupCapacitySpinner);
        JButton createButton = new JButton("Create Group");
        createButton.addActionListener(event -> createLearningGroup());
        GridBagConstraints createButtonConstraints = new GridBagConstraints();
        createButtonConstraints.gridx = 1;
        createButtonConstraints.gridy = 4;
        createButtonConstraints.anchor = GridBagConstraints.WEST;
        createButtonConstraints.insets = new Insets(3, 4, 3, 8);
        createForm.add(createButton, createButtonConstraints);
        JButton refreshButton = new JButton("Refresh Groups");
        refreshButton.addActionListener(event -> {
            refreshGroupStudentChoices();
            refreshGroupTables();
            refreshNotificationChoices();
            refreshNotificationTable();
            showGroupStatus("Learning groups refreshed.");
        });
        GridBagConstraints refreshConstraints = new GridBagConstraints();
        refreshConstraints.gridx = 2;
        refreshConstraints.gridy = 0;
        refreshConstraints.anchor = GridBagConstraints.WEST;
        refreshConstraints.insets = new Insets(3, 4, 3, 8);
        controls.add(refreshButton, refreshConstraints);
        JPanel top = new JPanel(new GridLayout(1, 2, 8, 4));
        top.add(controls);
        top.add(createForm);
        panel.add(top, BorderLayout.NORTH);

        learningGroupTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        learningGroupTable.setAutoCreateRowSorter(true);
        myGroupsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        myGroupsTable.setAutoCreateRowSorter(true);
        JPanel groupLists = createSegmentedCards(
                new String[]{"Group Discovery", "My Groups"},
                new Component[]{new JScrollPane(learningGroupTable), new JScrollPane(myGroupsTable)});

        JPanel details = new JPanel(new BorderLayout(5, 5));
        details.setBorder(BorderFactory.createTitledBorder("Group Details and Management"));
        JPanel detailHeader = new JPanel(new BorderLayout(4, 4));
        detailHeader.add(groupDetailsLabel, BorderLayout.CENTER);
        JPanel groupActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton joinLeaveButton = new JButton("Join / Leave");
        JButton editGroupButton = new JButton("Edit Group");
        JButton closeReopenButton = new JButton("Close / Reopen");
        JButton removeMemberButton = new JButton("Remove Selected Member");
        joinLeaveButton.addActionListener(event -> toggleGroupMembership());
        editGroupButton.addActionListener(event -> editLearningGroup());
        closeReopenButton.addActionListener(event -> toggleLearningGroupStatus());
        removeMemberButton.addActionListener(event -> removeSelectedGroupMember());
        groupActions.add(joinLeaveButton);
        groupActions.add(editGroupButton);
        groupActions.add(closeReopenButton);
        groupActions.add(removeMemberButton);
        detailHeader.add(groupActions, BorderLayout.SOUTH);
        details.add(detailHeader, BorderLayout.NORTH);

        JPanel membersPanel = new JPanel(new BorderLayout(4, 4));
        membersPanel.setBorder(BorderFactory.createTitledBorder("Group Members"));
        groupMemberTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        membersPanel.add(new JScrollPane(groupMemberTable), BorderLayout.CENTER);

        JPanel sessionsPanel = new JPanel(new BorderLayout(4, 4));
        sessionsPanel.setBorder(BorderFactory.createTitledBorder("Group Learning Sessions"));
        JPanel sessionControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sessionControls.add(new JLabel("View:"));
        sessionControls.add(groupSessionFilter);
        groupSessionFilter.addActionListener(event -> refreshSelectedLearningGroup());
        JButton scheduleButton = new JButton("Schedule Session");
        JButton rescheduleButton = new JButton("Reschedule");
        JButton cancelSessionButton = new JButton("Cancel");
        JButton completeSessionButton = new JButton("Mark Completed");
        scheduleButton.addActionListener(event -> scheduleGroupLearningSession());
        rescheduleButton.addActionListener(event -> rescheduleSelectedGroupSession());
        cancelSessionButton.addActionListener(event -> changeSelectedGroupSessionStatus(false));
        completeSessionButton.addActionListener(event -> changeSelectedGroupSessionStatus(true));
        sessionControls.add(scheduleButton);
        sessionControls.add(rescheduleButton);
        sessionControls.add(cancelSessionButton);
        sessionControls.add(completeSessionButton);
        sessionsPanel.add(sessionControls, BorderLayout.NORTH);
        groupSessionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupSessionTable.setAutoCreateRowSorter(true);
        sessionsPanel.add(new JScrollPane(groupSessionTable), BorderLayout.CENTER);

        JPanel sessionForm = new JPanel(new GridBagLayout());
        sessionForm.setBorder(BorderFactory.createTitledBorder("Schedule a Group Session"));
        addFormRow(sessionForm, 0, "Title:", groupSessionTitleField);
        addFormRow(sessionForm, 1, "Description:", new JScrollPane(groupSessionDescriptionArea));
        addFormRow(sessionForm, 2, "Date (YYYY-MM-DD):", groupSessionDateField);
        addFormRow(sessionForm, 3, "Time (HH:MM):", groupSessionTimeField);
        addFormRow(sessionForm, 4, "Duration (minutes):", groupSessionDurationSpinner);
        JSplitPane sessionSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sessionsPanel, sessionForm);
        sessionSplit.setResizeWeight(0.65);
        sessionSplit.setDividerLocation(230);
        JSplitPane detailSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, membersPanel, sessionSplit);
        detailSplit.setResizeWeight(0.30);
        detailSplit.setDividerLocation(145);
        details.add(detailSplit, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, groupLists, details);
        mainSplit.setResizeWeight(0.43);
        mainSplit.setDividerLocation(440);
        panel.add(mainSplit, BorderLayout.CENTER);
        panel.add(groupLearningStatusLabel, BorderLayout.SOUTH);

        learningGroupTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) selectGroupFromTable(learningGroupTable);
        });
        myGroupsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) selectGroupFromTable(myGroupsTable);
        });
        groupActingStudentCombo.addActionListener(event -> {
            if (!refreshingGroupChoices) {
                refreshGroupTables();
                refreshSelectedLearningGroup();
            }
        });
        groupStatusFilter.addActionListener(event -> refreshGroupTables());
        groupNameSearchField.getDocument().addDocumentListener(createGroupSearchListener());
        groupSkillSearchField.getDocument().addDocumentListener(createGroupSearchListener());
        setStudentChoiceRenderer(groupActingStudentCombo);
        return panel;
    }

    private DocumentListener createGroupSearchListener() {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) { refreshGroupTables(); }
            @Override
            public void removeUpdate(DocumentEvent event) { refreshGroupTables(); }
            @Override
            public void changedUpdate(DocumentEvent event) { refreshGroupTables(); }
        };
    }

    private void refreshGroupStudentChoices() {
        String selectedId = selectedStudentId(groupActingStudentCombo);
        List<StudentChoice> choices = studentManager.getAllStudents().stream()
                .map(StudentChoice::new).toList();
        refreshingGroupChoices = true;
        try {
            groupActingStudentCombo.setModel(new DefaultComboBoxModel<>(choices.toArray(StudentChoice[]::new)));
            selectStudentChoice(groupActingStudentCombo, selectedId);
        } finally {
            refreshingGroupChoices = false;
        }
    }

    private void refreshGroupTables() {
        learningGroupTableModel.setRowCount(0);
        myGroupsTableModel.setRowCount(0);
        StudentChoice actor = (StudentChoice) groupActingStudentCombo.getSelectedItem();
        String nameQuery = groupNameSearchField.getText().trim().toLowerCase(Locale.ROOT);
        String skillQuery = groupSkillSearchField.getText().trim().toLowerCase(Locale.ROOT);
        String filter = String.valueOf(groupStatusFilter.getSelectedItem());
        for (LearningGroup group : groupLearningManager.getAllGroups()) {
            if (!"All".equals(filter) && !filter.equalsIgnoreCase(group.getStatus().name())) continue;
            if (!group.getName().toLowerCase(Locale.ROOT).contains(nameQuery)
                    || !group.getLearningTopic().toLowerCase(Locale.ROOT).contains(skillQuery)) continue;
            String owner = studentDisplayName(group.getOwnerId());
            learningGroupTableModel.addRow(new Object[]{group.getGroupId(), group.getName(),
                    group.getLearningTopic(), owner, group.getMemberIds().size(),
                    group.getAvailableSeats(), formatGroupStatus(group.getStatus())});
        }
        if (actor != null) {
            for (LearningGroup group : groupLearningManager.getGroupsForStudent(actor.student().getId())) {
                myGroupsTableModel.addRow(new Object[]{group.getGroupId(), group.getName(),
                        group.getLearningTopic(), group.getMemberIds().size(),
                        formatGroupStatus(group.getStatus())});
            }
        }
        refreshSelectedLearningGroup();
    }

    private void selectGroupFromTable(JTable table) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) return;
        int modelRow = table.convertRowIndexToModel(selectedRow);
        selectedLearningGroupId = (Integer) (table == learningGroupTable
                ? learningGroupTableModel.getValueAt(modelRow, 0) : myGroupsTableModel.getValueAt(modelRow, 0));
        refreshSelectedLearningGroup();
    }

    private void refreshSelectedLearningGroup() {
        groupMemberTableModel.setRowCount(0);
        groupSessionTableModel.setRowCount(0);
        if (selectedLearningGroupId == null) {
            groupDetailsLabel.setText("Select a learning group to view its details.");
            return;
        }
        LearningGroup group;
        try {
            group = groupLearningManager.getGroup(selectedLearningGroupId);
        } catch (java.util.NoSuchElementException exception) {
            selectedLearningGroupId = null;
            groupDetailsLabel.setText(exception.getMessage());
            return;
        }
        groupDetailsLabel.setText(group.getName() + " — " + group.getLearningTopic()
                + " | Owner: " + studentDisplayName(group.getOwnerId()) + " | "
                + group.getMemberIds().size() + "/" + group.getMaximumMembers() + " members | "
                + formatGroupStatus(group.getStatus()) + " | " + group.getDescription());
        for (String memberId : group.getMemberIds()) {
            groupMemberTableModel.addRow(new Object[]{memberId, studentDisplayName(memberId),
                    memberId.equalsIgnoreCase(group.getOwnerId()) ? "Owner" : "Member"});
        }
        String sessionFilter = String.valueOf(groupSessionFilter.getSelectedItem());
        LocalDateTime now = LocalDateTime.now();
        for (GroupLearningSession session : groupLearningManager.getSessionsForGroup(group.getGroupId())) {
            LocalDateTime startsAt = LocalDateTime.of(session.getScheduledDate(), session.getScheduledTime());
            if ("Upcoming".equals(sessionFilter)
                    && (session.getStatus() != GroupLearningSession.Status.SCHEDULED || startsAt.isBefore(now)))
                continue;
            if ("Completed".equals(sessionFilter)
                    && session.getStatus() != GroupLearningSession.Status.COMPLETED) continue;
            if ("Cancelled".equals(sessionFilter)
                    && session.getStatus() != GroupLearningSession.Status.CANCELLED) continue;
            groupSessionTableModel.addRow(new Object[]{session.getGroupSessionId(), session.getTitle(),
                    session.getScheduledDate(), session.getScheduledTime(),
                    session.getDurationMinutes() + " minutes", studentDisplayName(session.getOrganizerId()),
                    session.getStatus().name()});
        }
        if (groupSessionTableModel.getRowCount() == 0)
            groupSessionTableModel.addRow(new Object[]{"—", "No sessions match this view", "—", "—", "—", "—", "—"});
    }

    private void createLearningGroup() {
        StudentChoice actor = (StudentChoice) groupActingStudentCombo.getSelectedItem();
        if (actor == null) {
            showGroupError("Register and select a student who will own the group.");
            return;
        }
        try {
            LearningGroup group = groupLearningManager.createGroup(actor.student().getId(), groupNameField.getText(),
                    groupDescriptionArea.getText(), groupTopicField.getText(),
                    (Integer) groupCapacitySpinner.getValue());
            boolean saved = saveData();
            groupNameField.setText("");
            groupTopicField.setText("");
            groupDescriptionArea.setText("");
            selectedLearningGroupId = group.getGroupId();
            refreshGroupTables();
            showGroupStatus("Learning group #" + group.getGroupId() + " created."
                    + (saved ? " Saved." : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showGroupError(exception.getMessage());
        }
    }

    private void toggleGroupMembership() {
        LearningGroup group = selectedLearningGroup();
        StudentChoice actor = (StudentChoice) groupActingStudentCombo.getSelectedItem();
        if (group == null) return;
        if (actor == null) {
            showGroupError("Select a student before joining or leaving a group.");
            return;
        }
        try {
            if (group.hasMember(actor.student().getId())) {
                groupLearningManager.leaveGroup(group.getGroupId(), actor.student().getId());
            } else {
                groupLearningManager.joinGroup(group.getGroupId(), actor.student().getId());
            }
            boolean saved = saveData();
            refreshAfterGroupChange();
            showGroupStatus((group.hasMember(actor.student().getId()) ? "Membership updated." : "You left the group.")
                    + (saved ? " Saved." : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showGroupError(exception.getMessage());
        }
    }

    private void editLearningGroup() {
        LearningGroup group = selectedLearningGroup();
        if (group == null) return;
        JTextField nameField = new JTextField(group.getName(), 22);
        JTextField topicField = new JTextField(group.getLearningTopic(), 22);
        JTextArea descriptionArea = new JTextArea(group.getDescription(), 4, 22);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JSpinner capacitySpinner = new JSpinner(
                new SpinnerNumberModel(group.getMaximumMembers(), group.getMemberIds().size(), 500, 1));
        JPanel form = new JPanel(new GridBagLayout());
        addFormRow(form, 0, "Group name:", nameField);
        addFormRow(form, 1, "Learning topic:", topicField);
        addFormRow(form, 2, "Description:", new JScrollPane(descriptionArea));
        addFormRow(form, 3, "Maximum members:", capacitySpinner);
        if (JOptionPane.showConfirmDialog(this, form, "Edit Learning Group",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        StudentChoice actor = (StudentChoice) groupActingStudentCombo.getSelectedItem();
        if (actor == null) return;
        try {
            groupLearningManager.updateGroup(group.getGroupId(), actor.student().getId(),
                    nameField.getText(), descriptionArea.getText(), topicField.getText(),
                    (Integer) capacitySpinner.getValue());
            boolean saved = saveData();
            refreshAfterGroupChange();
            showGroupStatus("Group details updated."
                    + (saved ? " Saved." : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showGroupError(exception.getMessage());
        }
    }

    private void toggleLearningGroupStatus() {
        LearningGroup group = selectedLearningGroup();
        StudentChoice actor = (StudentChoice) groupActingStudentCombo.getSelectedItem();
        if (group == null) return;
        if (actor == null) {
            showGroupError("Select a student before changing group status.");
            return;
        }
        try {
            LearningGroup.Status status = group.getStatus() == LearningGroup.Status.ACTIVE
                    ? LearningGroup.Status.CLOSED : LearningGroup.Status.ACTIVE;
            groupLearningManager.setGroupStatus(group.getGroupId(), actor.student().getId(), status);
            boolean saved = saveData();
            refreshAfterGroupChange();
            showGroupStatus("Group " + formatGroupStatus(status).toLowerCase(Locale.ROOT) + "."
                    + (saved ? " Saved." : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showGroupError(exception.getMessage());
        }
    }

    private void removeSelectedGroupMember() {
        LearningGroup group = selectedLearningGroup();
        if (group == null) return;
        int row = groupMemberTable.getSelectedRow();
        if (row < 0) {
            showGroupError("Select a group member to remove.");
            return;
        }
        int modelRow = groupMemberTable.convertRowIndexToModel(row);
        String memberId = (String) groupMemberTableModel.getValueAt(modelRow, 0);
        StudentChoice actor = (StudentChoice) groupActingStudentCombo.getSelectedItem();
        if (actor == null) return;
        try {
            groupLearningManager.removeMember(group.getGroupId(), actor.student().getId(), memberId);
            boolean saved = saveData();
            refreshAfterGroupChange();
            refreshNotificationChoices();
            refreshNotificationTable();
            showGroupStatus("Selected member removed."
                    + (saved ? " Saved." : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showGroupError(exception.getMessage());
        }
    }

    private void scheduleGroupLearningSession() {
        LearningGroup group = selectedLearningGroup();
        StudentChoice actor = (StudentChoice) groupActingStudentCombo.getSelectedItem();
        if (group == null) return;
        if (actor == null) {
            showGroupError("Select a group member to organize the session.");
            return;
        }
        try {
            GroupLearningSession session = groupLearningManager.scheduleSession(group.getGroupId(),
                    actor.student().getId(), groupSessionTitleField.getText(),
                    groupSessionDescriptionArea.getText(), parseGroupSessionDate(groupSessionDateField.getText()),
                    parseGroupSessionTime(groupSessionTimeField.getText()),
                    (Integer) groupSessionDurationSpinner.getValue());
            boolean saved = saveData();
            groupSessionTitleField.setText("");
            groupSessionDescriptionArea.setText("");
            refreshAfterGroupChange();
            refreshNotificationChoices();
            refreshNotificationTable();
            showGroupStatus("Group session #" + session.getGroupSessionId() + " scheduled."
                    + (saved ? " Members were notified and the session was saved."
                    : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showGroupError(exception.getMessage());
        }
    }

    private void rescheduleSelectedGroupSession() {
        GroupLearningSession session = selectedGroupLearningSession();
        if (session == null) return;
        JTextField titleField = new JTextField(session.getTitle(), 22);
        JTextArea descriptionArea = new JTextArea(session.getDescription(), 3, 22);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JTextField dateField = new JTextField(session.getScheduledDate().toString(), 12);
        JTextField timeField = new JTextField(session.getScheduledTime().toString(), 8);
        JSpinner durationSpinner = new JSpinner(
                new SpinnerNumberModel(session.getDurationMinutes(), 1, 1440, 15));
        JPanel form = new JPanel(new GridBagLayout());
        addFormRow(form, 0, "Title:", titleField);
        addFormRow(form, 1, "Description:", new JScrollPane(descriptionArea));
        addFormRow(form, 2, "Date (YYYY-MM-DD):", dateField);
        addFormRow(form, 3, "Time (HH:MM):", timeField);
        addFormRow(form, 4, "Duration (minutes):", durationSpinner);
        if (JOptionPane.showConfirmDialog(this, form, "Reschedule Group Session",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        StudentChoice actor = (StudentChoice) groupActingStudentCombo.getSelectedItem();
        if (actor == null) return;
        try {
            groupLearningManager.rescheduleSession(session.getGroupSessionId(), actor.student().getId(),
                    titleField.getText(), descriptionArea.getText(), parseGroupSessionDate(dateField.getText()),
                    parseGroupSessionTime(timeField.getText()), (Integer) durationSpinner.getValue());
            boolean saved = saveData();
            refreshAfterGroupChange();
            refreshNotificationChoices();
            refreshNotificationTable();
            showGroupStatus("Group session rescheduled."
                    + (saved ? " Group members were notified and the change was saved."
                    : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showGroupError(exception.getMessage());
        }
    }

    private void changeSelectedGroupSessionStatus(boolean complete) {
        GroupLearningSession session = selectedGroupLearningSession();
        StudentChoice actor = (StudentChoice) groupActingStudentCombo.getSelectedItem();
        if (session == null) return;
        if (actor == null) {
            showGroupError("Select an authorized group member.");
            return;
        }
        try {
            if (complete) groupLearningManager.completeSession(session.getGroupSessionId(), actor.student().getId());
            else groupLearningManager.cancelSession(session.getGroupSessionId(), actor.student().getId());
            boolean saved = saveData();
            refreshAfterGroupChange();
            if (!complete) {
                refreshNotificationChoices();
                refreshNotificationTable();
            }
            showGroupStatus("Group session " + (complete ? "completed." : "cancelled.")
                    + (saved ? (complete ? " Saved." : " Group members were notified and it was saved.")
                    : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showGroupError(exception.getMessage());
        }
    }

    private LearningGroup selectedLearningGroup() {
        if (selectedLearningGroupId == null) {
            showGroupError("Select a learning group first.");
            return null;
        }
        try {
            return groupLearningManager.getGroup(selectedLearningGroupId);
        } catch (java.util.NoSuchElementException exception) {
            showGroupError(exception.getMessage());
            return null;
        }
    }

    private GroupLearningSession selectedGroupLearningSession() {
        int row = groupSessionTable.getSelectedRow();
        if (row < 0) {
            showGroupError("Select a group session from the table first.");
            return null;
        }
        int modelRow = groupSessionTable.convertRowIndexToModel(row);
        Object value = groupSessionTableModel.getValueAt(modelRow, 0);
        if (!(value instanceof Integer sessionId)) {
            showGroupError("Select a valid group session.");
            return null;
        }
        try {
            return groupLearningManager.getSession(sessionId);
        } catch (java.util.NoSuchElementException exception) {
            showGroupError(exception.getMessage());
            refreshSelectedLearningGroup();
            return null;
        }
    }

    private void refreshAfterGroupChange() {
        refreshGroupTables();
        refreshSelectedLearningGroup();
        refreshPortfolioView();
    }

    private String studentDisplayName(String studentId) {
        return studentManager.findStudentById(studentId)
                .map(student -> student.getName() + " (" + student.getId() + ")")
                .orElse(studentId + " (missing student)");
    }

    private static String formatGroupStatus(LearningGroup.Status status) {
        return status == LearningGroup.Status.ACTIVE ? "Active" : "Closed";
    }

    private static LocalDate parseGroupSessionDate(String value) {
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Group session date must use YYYY-MM-DD format.");
        }
    }

    private static LocalTime parseGroupSessionTime(String value) {
        try {
            return LocalTime.parse(value.trim(),
                    DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Group session time must use HH:MM format.");
        }
    }

    private void showGroupError(String message) {
        JOptionPane.showMessageDialog(this, message, "Group Learning", JOptionPane.WARNING_MESSAGE);
        showGroupStatus(message);
    }

    private void showGroupStatus(String message) {
        groupLearningStatusLabel.setText(message);
    }

    private JPanel createRoadmapsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filters.add(new JLabel("Student:"));
        filters.add(roadmapStudentFilter);
        filters.add(new JLabel("Status:"));
        filters.add(roadmapStatusFilter);
        JButton createButton = new JButton("Create Roadmap");
        JButton refreshButton = new JButton("Refresh Roadmaps");
        createButton.addActionListener(event -> createRoadmapDialog());
        refreshButton.addActionListener(event -> {
            refreshRoadmapStudentChoices();
            refreshRoadmapTable();
            showRoadmapStatus("Roadmaps refreshed.");
        });
        filters.add(createButton);
        filters.add(refreshButton);
        panel.add(filters, BorderLayout.NORTH);

        JPanel roadmapPanel = new JPanel(new BorderLayout(5, 5));
        roadmapPanel.setBorder(BorderFactory.createTitledBorder("Learning Roadmaps"));
        roadmapTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roadmapTable.setAutoCreateRowSorter(true);
        roadmapPanel.add(new JScrollPane(roadmapTable), BorderLayout.CENTER);
        JPanel roadmapActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton editButton = new JButton("Edit Roadmap");
        JButton deleteButton = new JButton("Delete Roadmap");
        editButton.addActionListener(event -> editSelectedRoadmap());
        deleteButton.addActionListener(event -> deleteSelectedRoadmap());
        roadmapActions.add(editButton);
        roadmapActions.add(deleteButton);
        roadmapPanel.add(roadmapActions, BorderLayout.SOUTH);

        JPanel milestonePanel = new JPanel(new BorderLayout(5, 5));
        milestonePanel.setBorder(BorderFactory.createTitledBorder("Roadmap Milestones"));
        JPanel progressPanel = new JPanel(new BorderLayout(8, 4));
        progressPanel.add(roadmapDetailsLabel, BorderLayout.NORTH);
        roadmapProgressBar.setStringPainted(true);
        progressPanel.add(roadmapProgressBar, BorderLayout.CENTER);
        milestonePanel.add(progressPanel, BorderLayout.NORTH);
        roadmapMilestoneTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        milestonePanel.add(new JScrollPane(roadmapMilestoneTable), BorderLayout.CENTER);
        JPanel milestoneActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addMilestoneButton = new JButton("Add Milestone");
        JButton editMilestoneButton = new JButton("Edit Milestone");
        JButton toggleMilestoneButton = new JButton("Update Completion");
        addMilestoneButton.addActionListener(event -> addRoadmapMilestone());
        editMilestoneButton.addActionListener(event -> editSelectedMilestone());
        toggleMilestoneButton.addActionListener(event -> toggleSelectedMilestone());
        milestoneActions.add(addMilestoneButton);
        milestoneActions.add(editMilestoneButton);
        milestoneActions.add(toggleMilestoneButton);
        milestonePanel.add(milestoneActions, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, roadmapPanel, milestonePanel);
        splitPane.setResizeWeight(0.55);
        splitPane.setDividerLocation(290);
        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(roadmapStatusLabel, BorderLayout.SOUTH);

        roadmapStudentFilter.addActionListener(event -> refreshRoadmapTable());
        roadmapStatusFilter.addActionListener(event -> refreshRoadmapTable());
        roadmapTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) refreshSelectedRoadmapDetails();
        });
        roadmapStudentFilter.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value == null ? "" : value.toString());
                return this;
            }
        });
        return panel;
    }

    private void refreshRoadmapStudentChoices() {
        RoadmapStudentChoice selected = (RoadmapStudentChoice) roadmapStudentFilter.getSelectedItem();
        String selectedId = selected == null ? "" : selected.studentId();
        List<RoadmapStudentChoice> choices = new java.util.ArrayList<>();
        choices.add(new RoadmapStudentChoice("", "All Students"));
        studentManager.getAllStudents().stream()
                .map(student -> new RoadmapStudentChoice(student.getId(),
                        student.getName() + " (" + student.getId() + ")"))
                .forEach(choices::add);
        roadmapStudentFilter.setModel(new DefaultComboBoxModel<>(choices.toArray(RoadmapStudentChoice[]::new)));
        for (int index = 0; index < roadmapStudentFilter.getItemCount(); index++) {
            if (roadmapStudentFilter.getItemAt(index).studentId().equalsIgnoreCase(selectedId)) {
                roadmapStudentFilter.setSelectedIndex(index);
                break;
            }
        }
    }

    private void refreshRoadmapTable() {
        Integer selectedRoadmapId = selectedRoadmapIdOrNull();
        RoadmapStudentChoice studentFilter =
                (RoadmapStudentChoice) roadmapStudentFilter.getSelectedItem();
        String studentId = studentFilter == null ? "" : studentFilter.studentId();
        String statusFilter = String.valueOf(roadmapStatusFilter.getSelectedItem());
        roadmapTableModel.setRowCount(0);
        for (LearningRoadmap roadmap : learningRoadmapManager.getAllRoadmaps()) {
            if (!studentId.isBlank() && !roadmap.getStudentId().equalsIgnoreCase(studentId)) continue;
            String status = roadmap.getStatus().name();
            if (!"All".equals(statusFilter)
                    && !statusFilter.replace(' ', '_').equalsIgnoreCase(status)) continue;
            Student student = studentManager.findStudentById(roadmap.getStudentId()).orElse(null);
            String studentName = student == null ? roadmap.getStudentId()
                    : student.getName() + " (" + student.getId() + ")";
            roadmapTableModel.addRow(new Object[]{
                    roadmap.getRoadmapId(), studentName, roadmap.getTitle(), roadmap.getTargetSkill(),
                    roadmap.getCreationDate(), roadmap.getTargetCompletionDate(),
                    roadmap.getProgressPercent() + "%", formatRoadmapStatus(roadmap.getStatus())
            });
        }
        if (selectedRoadmapId != null) {
            selectRoadmapRow(selectedRoadmapId);
        }
        if (roadmapTableModel.getRowCount() == 0) {
            roadmapMilestoneTableModel.setRowCount(0);
            roadmapDetailsLabel.setText("Select a roadmap to view its milestones.");
            roadmapProgressBar.setValue(0);
            roadmapProgressBar.setString("0%");
            showRoadmapStatus(learningRoadmapManager.getAllRoadmaps().isEmpty()
                    ? "No learning roadmaps have been created yet."
                    : "No roadmaps match the selected filters.");
        } else if (roadmapTable.getSelectedRow() < 0) {
            roadmapTable.setRowSelectionInterval(0, 0);
        }
    }

    private Integer selectedRoadmapIdOrNull() {
        int selectedRow = roadmapTable.getSelectedRow();
        if (selectedRow < 0) return null;
        int modelRow = roadmapTable.convertRowIndexToModel(selectedRow);
        return (Integer) roadmapTableModel.getValueAt(modelRow, 0);
    }

    private void selectRoadmapRow(int roadmapId) {
        for (int modelRow = 0; modelRow < roadmapTableModel.getRowCount(); modelRow++) {
            if ((Integer) roadmapTableModel.getValueAt(modelRow, 0) == roadmapId) {
                int viewRow = roadmapTable.convertRowIndexToView(modelRow);
                roadmapTable.setRowSelectionInterval(viewRow, viewRow);
                return;
            }
        }
    }

    private LearningRoadmap selectedRoadmap() {
        Integer roadmapId = selectedRoadmapIdOrNull();
        if (roadmapId == null) {
            showRoadmapError("Select a learning roadmap first.");
            return null;
        }
        try {
            return learningRoadmapManager.getRoadmap(roadmapId);
        } catch (java.util.NoSuchElementException exception) {
            showRoadmapError(exception.getMessage());
            refreshRoadmapTable();
            return null;
        }
    }

    private void refreshSelectedRoadmapDetails() {
        LearningRoadmap roadmap = selectedRoadmapIdOrNull() == null
                ? null : learningRoadmapManager.getRoadmap(selectedRoadmapIdOrNull());
        roadmapMilestoneTableModel.setRowCount(0);
        if (roadmap == null) {
            roadmapDetailsLabel.setText("Select a roadmap to view its milestones.");
            roadmapProgressBar.setValue(0);
            roadmapProgressBar.setString("0%");
            return;
        }
        roadmapDetailsLabel.setText("Roadmap #" + roadmap.getRoadmapId() + " — "
                + roadmap.getTitle() + " | " + formatRoadmapStatus(roadmap.getStatus()));
        roadmapProgressBar.setValue(roadmap.getProgressPercent());
        roadmapProgressBar.setString(roadmap.getProgressPercent() + "%");
        for (RoadmapMilestone milestone : roadmap.getMilestones()) {
            roadmapMilestoneTableModel.addRow(new Object[]{
                    milestone.getMilestoneId(), milestone.getTitle(), milestone.getDescription(),
                    milestone.getDueDate(), milestone.isCompleted() ? "Completed" : "Not Completed"
            });
        }
        if (roadmap.getMilestones().isEmpty())
            showRoadmapStatus("Add milestones to calculate roadmap progress.");
    }

    private void createRoadmapDialog() {
        List<StudentChoice> students = studentManager.getAllStudents().stream()
                .map(StudentChoice::new).toList();
        if (students.isEmpty()) {
            showRoadmapError("Register a student before creating a learning roadmap.");
            return;
        }
        JComboBox<StudentChoice> studentCombo = new JComboBox<>(students.toArray(StudentChoice[]::new));
        setStudentChoiceRenderer(studentCombo);
        JComboBox<String> skillCombo = new JComboBox<>();
        skillCombo.setEditable(true);
        JTextField titleField = new JTextField(22);
        JTextField targetDateField = new JTextField(LocalDate.now().plusMonths(1).toString(), 12);
        JTextArea descriptionArea = new JTextArea(3, 22);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        Runnable updateSkills = () -> populateRoadmapSkillChoices(skillCombo,
                ((StudentChoice) studentCombo.getSelectedItem()));
        studentCombo.addActionListener(event -> updateSkills.run());
        updateSkills.run();

        JPanel form = new JPanel(new GridBagLayout());
        addFormRow(form, 0, "Student:", studentCombo);
        addFormRow(form, 1, "Roadmap title:", titleField);
        addFormRow(form, 2, "Target skill:", skillCombo);
        addFormRow(form, 3, "Description:", new JScrollPane(descriptionArea));
        addFormRow(form, 4, "Target completion (YYYY-MM-DD):", targetDateField);
        if (JOptionPane.showConfirmDialog(this, form, "Create Learning Roadmap",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        StudentChoice student = (StudentChoice) studentCombo.getSelectedItem();
        if (student == null) {
            showRoadmapError("Select a student for this roadmap.");
            return;
        }
        try {
            LearningRoadmap roadmap = learningRoadmapManager.createRoadmap(student.student().getId(),
                    titleField.getText(), selectedComboText(skillCombo), descriptionArea.getText(),
                    parseRoadmapDate(targetDateField.getText(), "Target completion date"));
            boolean saved = saveData();
            refreshRoadmapStudentChoices();
            selectRoadmapStudent(student.student().getId());
            refreshRoadmapTable();
            selectRoadmapRow(roadmap.getRoadmapId());
            refreshSelectedRoadmapDetails();
            showRoadmapStatus("Roadmap #" + roadmap.getRoadmapId() + " created."
                    + (saved ? " Saved." : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showRoadmapError(exception.getMessage());
        }
    }

    private void editSelectedRoadmap() {
        LearningRoadmap roadmap = selectedRoadmap();
        if (roadmap == null) return;
        Student student = studentManager.findStudentById(roadmap.getStudentId()).orElse(null);
        if (student == null) {
            showRoadmapError("The roadmap's student profile no longer exists.");
            return;
        }
        JTextField titleField = new JTextField(roadmap.getTitle(), 22);
        JComboBox<String> skillCombo = new JComboBox<>();
        skillCombo.setEditable(true);
        populateRoadmapSkillChoices(skillCombo, new StudentChoice(student));
        skillCombo.setSelectedItem(roadmap.getTargetSkill());
        JTextArea descriptionArea = new JTextArea(roadmap.getDescription(), 3, 22);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JTextField targetDateField = new JTextField(roadmap.getTargetCompletionDate().toString(), 12);
        JPanel form = new JPanel(new GridBagLayout());
        addFormRow(form, 0, "Roadmap title:", titleField);
        addFormRow(form, 1, "Target skill:", skillCombo);
        addFormRow(form, 2, "Description:", new JScrollPane(descriptionArea));
        addFormRow(form, 3, "Target completion (YYYY-MM-DD):", targetDateField);
        if (JOptionPane.showConfirmDialog(this, form, "Edit Roadmap #" + roadmap.getRoadmapId(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            learningRoadmapManager.updateRoadmap(roadmap.getRoadmapId(), roadmap.getStudentId(),
                    titleField.getText(), selectedComboText(skillCombo), descriptionArea.getText(),
                    parseRoadmapDate(targetDateField.getText(), "Target completion date"));
            boolean saved = saveData();
            refreshRoadmapTable();
            refreshSelectedRoadmapDetails();
            showRoadmapStatus("Roadmap #" + roadmap.getRoadmapId() + " updated."
                    + (saved ? " Saved." : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showRoadmapError(exception.getMessage());
        }
    }

    private void addRoadmapMilestone() {
        LearningRoadmap roadmap = selectedRoadmap();
        if (roadmap == null) return;
        JTextField titleField = new JTextField(22);
        JTextArea descriptionArea = new JTextArea(3, 22);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JTextField dueDateField = new JTextField(roadmap.getTargetCompletionDate().toString(), 12);
        JPanel form = new JPanel(new GridBagLayout());
        addFormRow(form, 0, "Milestone title:", titleField);
        addFormRow(form, 1, "Description:", new JScrollPane(descriptionArea));
        addFormRow(form, 2, "Due date (YYYY-MM-DD):", dueDateField);
        if (JOptionPane.showConfirmDialog(this, form, "Add Milestone",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            RoadmapMilestone milestone = learningRoadmapManager.addMilestone(roadmap.getRoadmapId(),
                    titleField.getText(), descriptionArea.getText(),
                    parseRoadmapDate(dueDateField.getText(), "Milestone due date"));
            boolean saved = saveData();
            refreshRoadmapTable();
            refreshSelectedRoadmapDetails();
            selectMilestoneRow(milestone.getMilestoneId());
            showRoadmapStatus("Milestone added to roadmap #" + roadmap.getRoadmapId() + "."
                    + (saved ? " Saved." : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showRoadmapError(exception.getMessage());
        }
    }

    private void editSelectedMilestone() {
        LearningRoadmap roadmap = selectedRoadmap();
        RoadmapMilestone milestone = selectedMilestone(roadmap);
        if (roadmap == null || milestone == null) return;
        JTextField titleField = new JTextField(milestone.getTitle(), 22);
        JTextArea descriptionArea = new JTextArea(milestone.getDescription(), 3, 22);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JTextField dueDateField = new JTextField(milestone.getDueDate().toString(), 12);
        JPanel form = new JPanel(new GridBagLayout());
        addFormRow(form, 0, "Milestone title:", titleField);
        addFormRow(form, 1, "Description:", new JScrollPane(descriptionArea));
        addFormRow(form, 2, "Due date (YYYY-MM-DD):", dueDateField);
        if (JOptionPane.showConfirmDialog(this, form, "Edit Milestone",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            learningRoadmapManager.updateMilestone(roadmap.getRoadmapId(), milestone.getMilestoneId(),
                    titleField.getText(), descriptionArea.getText(),
                    parseRoadmapDate(dueDateField.getText(), "Milestone due date"));
            boolean saved = saveData();
            refreshRoadmapTable();
            refreshSelectedRoadmapDetails();
            showRoadmapStatus("Milestone updated."
                    + (saved ? " Saved." : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showRoadmapError(exception.getMessage());
        }
    }

    private void toggleSelectedMilestone() {
        LearningRoadmap roadmap = selectedRoadmap();
        RoadmapMilestone milestone = selectedMilestone(roadmap);
        if (roadmap == null || milestone == null) return;
        boolean completed = !milestone.isCompleted();
        try {
            learningRoadmapManager.setMilestoneCompleted(roadmap.getRoadmapId(),
                    milestone.getMilestoneId(), completed);
            RewardManager.RewardState rewardState = rewardManager.snapshot();
            long pointsBefore = rewardManager.getTotalPoints(roadmap.getStudentId());
            if (completed) rewardManager.awardCompletedMilestone(
                    roadmap.getRoadmapId(), milestone.getMilestoneId());
            boolean saved = saveData();
            if (!saved && completed) rewardManager.restoreState(rewardState);
            long pointsAwarded = saved && completed
                    ? rewardManager.getTotalPoints(roadmap.getStudentId()) - pointsBefore : 0;
            refreshRoadmapTable();
            refreshSelectedRoadmapDetails();
            refreshRewardsView();
            refreshPortfolioView();
            showRoadmapStatus("Milestone marked " + (completed ? "completed." : "not completed.")
                    + (saved ? (completed ? " " + pointsAwarded + " reward point(s) awarded. Saved."
                    : " Saved.") : " The change remains in memory; rewards were not recorded because saving failed."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showRoadmapError(exception.getMessage());
        }
    }

    private void deleteSelectedRoadmap() {
        LearningRoadmap roadmap = selectedRoadmap();
        if (roadmap == null) return;
        if (!learningRoadmapManager.canDeleteRoadmap(roadmap.getRoadmapId())) {
            showRoadmapError("Completed roadmaps cannot be deleted.");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete roadmap #" + roadmap.getRoadmapId() + " and all its milestones?",
                "Confirm Roadmap Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        try {
            learningRoadmapManager.deleteRoadmap(roadmap.getRoadmapId());
            boolean saved = saveData();
            refreshRoadmapTable();
            showRoadmapStatus("Roadmap deleted."
                    + (saved ? " Saved." : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showRoadmapError(exception.getMessage());
        }
    }

    private RoadmapMilestone selectedMilestone(LearningRoadmap roadmap) {
        if (roadmap == null) return null;
        int selectedRow = roadmapMilestoneTable.getSelectedRow();
        if (selectedRow < 0) {
            showRoadmapError("Select a milestone first.");
            return null;
        }
        int modelRow = roadmapMilestoneTable.convertRowIndexToModel(selectedRow);
        int milestoneId = (Integer) roadmapMilestoneTableModel.getValueAt(modelRow, 0);
        try {
            return roadmap.getMilestone(milestoneId);
        } catch (java.util.NoSuchElementException exception) {
            showRoadmapError(exception.getMessage());
            refreshSelectedRoadmapDetails();
            return null;
        }
    }

    private void selectMilestoneRow(int milestoneId) {
        for (int row = 0; row < roadmapMilestoneTableModel.getRowCount(); row++) {
            if ((Integer) roadmapMilestoneTableModel.getValueAt(row, 0) == milestoneId) {
                roadmapMilestoneTable.setRowSelectionInterval(row, row);
                return;
            }
        }
    }

    private void selectRoadmapStudent(String studentId) {
        for (int index = 0; index < roadmapStudentFilter.getItemCount(); index++) {
            if (roadmapStudentFilter.getItemAt(index).studentId().equalsIgnoreCase(studentId)) {
                roadmapStudentFilter.setSelectedIndex(index);
                return;
            }
        }
    }

    private static void populateRoadmapSkillChoices(JComboBox<String> combo, StudentChoice studentChoice) {
        Object previous = combo.getEditor().getItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        if (studentChoice != null) {
            java.util.stream.Stream.concat(studentChoice.student().getSkillsToLearn().stream(),
                            studentChoice.student().getSkillsToTeach().stream())
                    .map(Skill::getName)
                    .distinct()
                    .forEach(model::addElement);
        }
        combo.setModel(model);
        combo.setEditable(true);
        if (previous != null && !previous.toString().isBlank()) combo.setSelectedItem(previous);
    }

    private static String selectedComboText(JComboBox<String> combo) {
        Object selected = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }

    private static LocalDate parseRoadmapDate(String text, String field) {
        try {
            return LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (java.time.format.DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException(field + " must use YYYY-MM-DD format.");
        }
    }

    private static String formatRoadmapStatus(LearningRoadmap.Status status) {
        return switch (status) {
            case NOT_STARTED -> "Not Started";
            case IN_PROGRESS -> "In Progress";
            case COMPLETED -> "Completed";
        };
    }

    private void showRoadmapError(String message) {
        JOptionPane.showMessageDialog(this, message, "Learning Roadmaps", JOptionPane.WARNING_MESSAGE);
        showRoadmapStatus(message);
    }

    private void showRoadmapStatus(String message) {
        roadmapStatusLabel.setText(message);
    }

    private void setStudentChoiceRenderer(JComboBox<StudentChoice> combo) {
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value == null ? "" : value.toString());
                return this;
            }
        });
    }

    private void refreshSessionChoices() {
        String teacherId = selectedStudentId(sessionTeacherCombo);
        String learnerId = selectedStudentId(sessionLearnerCombo);
        List<StudentChoice> choices = studentManager.getAllStudents().stream()
                .map(StudentChoice::new)
                .toList();
        refreshingSessionChoices = true;
        try {
            sessionTeacherCombo.setModel(new DefaultComboBoxModel<>(choices.toArray(StudentChoice[]::new)));
            sessionLearnerCombo.setModel(new DefaultComboBoxModel<>(choices.toArray(StudentChoice[]::new)));
            selectStudentChoice(sessionTeacherCombo, teacherId);
            selectStudentChoice(sessionLearnerCombo, learnerId);
            if (choices.size() > 1 && selectedStudentId(sessionTeacherCombo)
                    .equalsIgnoreCase(selectedStudentId(sessionLearnerCombo))) {
                sessionLearnerCombo.setSelectedIndex(1);
            }
        } finally {
            refreshingSessionChoices = false;
        }
        updateSessionSkillChoices();
    }

    private static String selectedStudentId(JComboBox<StudentChoice> combo) {
        StudentChoice selected = (StudentChoice) combo.getSelectedItem();
        return selected == null ? "" : selected.student().getId();
    }

    private static void selectStudentChoice(JComboBox<StudentChoice> combo, String studentId) {
        if (studentId == null || studentId.isBlank()) return;
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (combo.getItemAt(index).student().getId().equalsIgnoreCase(studentId)) {
                combo.setSelectedIndex(index);
                return;
            }
        }
    }

    private void updateSessionSkillChoices() {
        StudentChoice teacherChoice = (StudentChoice) sessionTeacherCombo.getSelectedItem();
        StudentChoice learnerChoice = (StudentChoice) sessionLearnerCombo.getSelectedItem();
        String previousSkill = sessionSkillCombo.getSelectedItem() instanceof Skill skill
                ? skill.getName() : "";
        DefaultComboBoxModel<Skill> choices = new DefaultComboBoxModel<>();
        if (teacherChoice != null && learnerChoice != null
                && !teacherChoice.student().getId().equalsIgnoreCase(learnerChoice.student().getId())) {
            Set<String> wantedSkills = learnerChoice.student().getSkillsToLearn().stream()
                    .map(skill -> skill.getName().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            teacherChoice.student().getSkillsToTeach().stream()
                    .filter(skill -> wantedSkills.contains(skill.getName().toLowerCase(Locale.ROOT)))
                    .forEach(choices::addElement);
        }
        sessionSkillCombo.setModel(choices);
        if (!previousSkill.isBlank()) {
            for (int index = 0; index < choices.getSize(); index++) {
                if (choices.getElementAt(index).getName().equalsIgnoreCase(previousSkill)) {
                    sessionSkillCombo.setSelectedIndex(index);
                    break;
                }
            }
        }
    }

    private void refreshSessionTable() {
        String filter = String.valueOf(sessionStatusFilter.getSelectedItem());
        String query = sessionSearchField.getText().trim().toLowerCase(Locale.ROOT);
        sessionTableModel.setRowCount(0);
        for (Session session : sessionManager.getAllSessions()) {
            String status = session.getStatus().name();
            if (!"All Sessions".equals(filter) && !filter.equalsIgnoreCase(status)) continue;
            String searchable = (session.getTeacher().getName() + " "
                    + session.getLearner().getName() + " " + session.getSkill().getName())
                    .toLowerCase(Locale.ROOT);
            if (!searchable.contains(query)) continue;
            sessionTableModel.addRow(new Object[]{
                    session.getSessionId(),
                    session.getTeacher().getName(),
                    session.getLearner().getName(),
                    session.getSkill().getName(),
                    session.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                    session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                    session.getDurationMinutes() + " minutes",
                    status
            });
        }
    }

    private void scheduleSession() {
        StudentChoice teacher = (StudentChoice) sessionTeacherCombo.getSelectedItem();
        StudentChoice learner = (StudentChoice) sessionLearnerCombo.getSelectedItem();
        Skill skill = (Skill) sessionSkillCombo.getSelectedItem();
        if (teacher == null || learner == null || skill == null
                || sessionDateField.getText().isBlank() || sessionTimeField.getText().isBlank()) {
            showSessionError("Select a teacher, learner, shared skill, date, and time.");
            return;
        }
        if (teacher.student().getId().equalsIgnoreCase(learner.student().getId())) {
            showSessionError("Teacher and learner must be different students.");
            return;
        }
        try {
            Session session = sessionManager.createSession(teacher.student().getId(),
                    learner.student().getId(), skill.getName(), parseSessionDate(sessionDateField.getText()),
                    parseSessionTime(sessionTimeField.getText()), (Integer) sessionDurationSpinner.getValue());
            createNotificationIfAbsent(session.getTeacher().getId(), "SESSION_CREATED",
                    "Session #" + session.getSessionId() + " was scheduled with "
                            + session.getLearner().getName() + ".");
            createNotificationIfAbsent(session.getLearner().getId(), "SESSION_CREATED",
                    "Session #" + session.getSessionId() + " was scheduled with "
                            + session.getTeacher().getName() + ".");
            refreshSessionTable();
            boolean saved = saveData();
            refreshNotificationsAfterOperation();
            refreshRatingViews();
            refreshPortfolioView();
            showSessionStatus("Session #" + session.getSessionId() + " scheduled."
                    + (saved ? " Saved." : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showSessionError(exception.getMessage());
        }
    }

    private void viewSelectedSession() {
        Session session = selectedSession();
        if (session == null) return;
        String details = "Session ID: " + session.getSessionId()
                + "\nTeacher: " + session.getTeacher().getName() + " (" + session.getTeacher().getId() + ")"
                + "\nLearner: " + session.getLearner().getName() + " (" + session.getLearner().getId() + ")"
                + "\nSkill: " + session.getSkill()
                + "\nDate: " + session.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                + "\nTime: " + session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                + "\nDuration: " + session.getDurationMinutes() + " minutes"
                + "\nStatus: " + session.getStatus();
        JOptionPane.showMessageDialog(this, details, "Session Details", JOptionPane.INFORMATION_MESSAGE);
        showSessionStatus("Viewing session #" + session.getSessionId() + ".");
    }

    private void rescheduleSelectedSession() {
        Session session = selectedSession();
        if (session == null) return;
        if (session.getStatus() != Session.Status.SCHEDULED) {
            showSessionError("Only scheduled sessions can be rescheduled.");
            return;
        }

        JComboBox<StudentChoice> participantCombo = new JComboBox<>(new StudentChoice[]{
                new StudentChoice(session.getTeacher()), new StudentChoice(session.getLearner())});
        setStudentChoiceRenderer(participantCombo);
        JTextField dateField = new JTextField(session.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE), 12);
        JTextField timeField = new JTextField(session.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")), 8);
        JSpinner durationSpinner = new JSpinner(
                new SpinnerNumberModel(session.getDurationMinutes(), 1, 1440, 15));
        JPanel form = new JPanel(new GridBagLayout());
        addFormRow(form, 0, "Participant:", participantCombo);
        addFormRow(form, 1, "New date (YYYY-MM-DD):", dateField);
        addFormRow(form, 2, "New time (HH:MM):", timeField);
        addFormRow(form, 3, "Duration (minutes):", durationSpinner);
        if (JOptionPane.showConfirmDialog(this, form, "Reschedule Session #" + session.getSessionId(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        StudentChoice participant = (StudentChoice) participantCombo.getSelectedItem();
        if (participant == null) {
            showSessionError("Select a session participant.");
            return;
        }
        try {
            LocalDate previousDate = session.getDate();
            LocalTime previousTime = session.getStartTime();
            sessionManager.updateSession(session.getSessionId(), participant.student().getId(),
                    parseSessionDate(dateField.getText()), parseSessionTime(timeField.getText()),
                    (Integer) durationSpinner.getValue());
            Student otherParticipant = session.getTeacher().getId()
                    .equalsIgnoreCase(participant.student().getId())
                    ? session.getLearner() : session.getTeacher();
            createNotificationIfAbsent(otherParticipant.getId(), "SESSION_RESCHEDULED",
                    "Session #" + session.getSessionId() + " was rescheduled from "
                            + previousDate + " at " + previousTime + " to "
                            + session.getDate() + " at " + session.getStartTime() + ".");
            refreshSessionTable();
            boolean saved = saveData();
            refreshNotificationsAfterOperation();
            refreshRatingViews();
            refreshPortfolioView();
            showSessionStatus("Session #" + session.getSessionId() + " rescheduled."
                    + (saved ? " Saved." : " The change remains in memory and was not saved."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showSessionError(exception.getMessage());
        }
    }

    private void changeSelectedSessionStatus(boolean complete) {
        Session session = selectedSession();
        if (session == null) return;
        if (session.getStatus() != Session.Status.SCHEDULED) {
            showSessionError("Only scheduled sessions can be completed or cancelled.");
            return;
        }
        JComboBox<StudentChoice> participantCombo = new JComboBox<>(new StudentChoice[]{
                new StudentChoice(session.getTeacher()), new StudentChoice(session.getLearner())});
        setStudentChoiceRenderer(participantCombo);
        JPanel confirmation = new JPanel(new GridBagLayout());
        addFormRow(confirmation, 0, "Participant:", participantCombo);
        String statusLabel = complete ? "completed" : "cancelled";
        if (JOptionPane.showConfirmDialog(this, confirmation,
                "Confirm " + (complete ? "Completion" : "Cancellation"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        StudentChoice participant = (StudentChoice) participantCombo.getSelectedItem();
        if (participant == null) {
            showSessionError("Select a session participant.");
            return;
        }
        try {
            Student otherParticipant = session.getTeacher().getId()
                    .equalsIgnoreCase(participant.student().getId())
                    ? session.getLearner() : session.getTeacher();
            RewardManager.RewardState rewardState = rewardManager.snapshot();
            long pointsBefore = rewardManager.getTotalPoints(session.getTeacher().getId())
                    + rewardManager.getTotalPoints(session.getLearner().getId());
            if (complete) {
                sessionManager.completeSession(session.getSessionId(), participant.student().getId());
                rewardManager.awardCompletedSession(session);
            } else {
                sessionManager.cancelSession(session.getSessionId(), participant.student().getId());
            }
            String eventType = complete ? "SESSION_COMPLETED" : "SESSION_CANCELLED";
            createNotificationIfAbsent(otherParticipant.getId(), eventType,
                    "Session #" + session.getSessionId() + " was "
                            + (complete ? "completed." : "cancelled."));
            refreshSessionTable();
            boolean saved = saveData();
            if (!saved && complete) rewardManager.restoreState(rewardState);
            long pointsAwarded = saved && complete
                    ? rewardManager.getTotalPoints(session.getTeacher().getId())
                    + rewardManager.getTotalPoints(session.getLearner().getId()) - pointsBefore : 0;
            refreshNotificationsAfterOperation();
            refreshRatingViews();
            refreshPortfolioView();
            refreshRewardsView();
            showSessionStatus("Session #" + session.getSessionId() + " " + statusLabel + "."
                    + (saved ? (complete ? " " + pointsAwarded + " reward point(s) awarded to participants."
                    : " Saved.") : " The change remains in memory; rewards were not recorded because saving failed."));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showSessionError(exception.getMessage());
        }
    }

    private Session selectedSession() {
        int selectedRow = sessionTable.getSelectedRow();
        if (selectedRow < 0) {
            showSessionError("Select a session from the table first.");
            return null;
        }
        int modelRow = sessionTable.convertRowIndexToModel(selectedRow);
        int sessionId = (Integer) sessionTableModel.getValueAt(modelRow, 0);
        try {
            return sessionManager.getSession(sessionId);
        } catch (java.util.NoSuchElementException exception) {
            showSessionError(exception.getMessage());
            refreshSessionTable();
            return null;
        }
    }

    private static LocalDate parseSessionDate(String value) {
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Session date must be a valid date in YYYY-MM-DD format.");
        }
    }

    private static LocalTime parseSessionTime(String value) {
        try {
            return LocalTime.parse(value.trim(),
                    DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Session time must be a valid time in HH:MM format.");
        }
    }

    private void showSessionError(String message) {
        JOptionPane.showMessageDialog(this, message, "Session Management", JOptionPane.WARNING_MESSAGE);
        showSessionStatus(message);
    }

    private void showSessionStatus(String message) {
        sessionStatusLabel.setText(message);
    }

    private void addFormRow(JPanel panel, int row, String labelText, JTextField field) {
        addFormRow(panel, row, labelText, (java.awt.Component) field);
    }

    private void addFormRow(JPanel panel, int row, String labelText, java.awt.Component input) {
        JLabel label = new JLabel(labelText, SwingConstants.LEFT);
        label.putClientProperty("skillora.formLabel", Boolean.TRUE);
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(6, 6, 6, 10);
        panel.add(label, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(5, 4, 5, 8);
        panel.add(input, fieldConstraints);
    }

    private void registerStudent() {
        try {
            studentManager.registerStudent(readStudentFromForm());
            refreshTable(studentManager.getAllStudents());
            refreshSessionChoices();
            refreshSessionTable();
            refreshRoadmapStudentChoices();
            refreshRoadmapTable();
            refreshNotificationChoices();
            refreshNotificationTable();
            refreshRatingStudentChoices();
            refreshRatingViews();
            refreshPortfolioStudentChoices();
            refreshPortfolioView();
            refreshRewardStudentChoices();
            refreshRewardsView();
            refreshGroupStudentChoices();
            refreshGroupTables();
            clearForm();
            boolean saved = saveData();
            showProfileStatus("Student profile registered."
                    + (saved ? " Saved." : " Changes remain in memory."));
        } catch (IllegalArgumentException exception) {
            showProfileError(exception.getMessage());
        }
    }

    private void updateStudent() {
        if (selectedStudentId == null) {
            showProfileError("Select a student profile from the table before updating.");
            return;
        }

        try {
            Student updatedStudent = readStudentFromForm();
            Student existingStudent = studentManager.findStudentById(selectedStudentId)
                    .orElseThrow(() -> new java.util.NoSuchElementException(
                            "The student profile no longer exists."));
            String previousId = existingStudent.getId();
            existingStudent.getSkillsToTeach().forEach(updatedStudent::addSkillToTeach);
            existingStudent.getSkillsToLearn().forEach(updatedStudent::addSkillToLearn);
            studentManager.updateStudent(selectedStudentId, updatedStudent);
            if (!previousId.equals(updatedStudent.getId())) {
                notificationManager.updateStudentId(previousId, updatedStudent.getId());
                learningRoadmapManager.updateStudentId(previousId, updatedStudent.getId());
                rewardManager.updateStudentId(previousId, updatedStudent.getId());
                groupLearningManager.updateStudentId(previousId, updatedStudent.getId());
            }
            refreshTable(studentManager.getAllStudents());
            refreshSessionChoices();
            refreshSessionTable();
            refreshRoadmapStudentChoices();
            refreshRoadmapTable();
            refreshNotificationChoices();
            refreshNotificationTable();
            refreshRatingStudentChoices();
            refreshRatingViews();
            refreshPortfolioStudentChoices();
            refreshPortfolioView();
            refreshRewardStudentChoices();
            refreshRewardsView();
            refreshGroupStudentChoices();
            refreshGroupTables();
            clearForm();
            boolean saved = saveData();
            showProfileStatus("Student profile updated."
                    + (saved ? " Saved." : " Changes remain in memory."));
        } catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
            showProfileError(exception.getMessage());
        }
    }

    private void deleteStudent() {
        if (selectedStudentId == null) {
            showProfileError("Select a student profile from the table before deleting.");
            return;
        }
        if (sessionManager.hasSessionsForStudent(selectedStudentId)) {
            showProfileError("Student profiles referenced by session records cannot be deleted.");
            return;
        }
        if (learningRoadmapManager.hasRoadmapsForStudent(selectedStudentId)) {
            showProfileError("Delete the student's roadmaps before deleting this profile.");
            return;
        }
        if (rewardManager.hasRewardsForStudent(selectedStudentId)) {
            showProfileError("Student profiles with reward history or earned badges cannot be deleted.");
            return;
        }
        if (groupLearningManager.hasReferencesForStudent(selectedStudentId)) {
            showProfileError("Student profiles that own or belong to learning groups cannot be deleted.");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "Delete the selected student profile and any exchange requests involving them?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            int removedNotifications = notificationManager.deleteNotificationsForStudent(selectedStudentId);
            int removedRequests = exchangeRequestManager.deleteRequestsForStudent(selectedStudentId);
            studentManager.deleteStudent(selectedStudentId);
            refreshTable(studentManager.getAllStudents());
            refreshRequestTable();
            refreshSessionChoices();
            refreshSessionTable();
            refreshRoadmapStudentChoices();
            refreshRoadmapTable();
            refreshNotificationChoices();
            refreshNotificationTable();
            refreshRatingStudentChoices();
            refreshRatingViews();
            refreshPortfolioStudentChoices();
            refreshPortfolioView();
            refreshRewardStudentChoices();
            refreshRewardsView();
            refreshGroupStudentChoices();
            refreshGroupTables();
            skillTableModel.setRowCount(0);
            clearForm();
            boolean saved = saveData();
            showProfileStatus("Student profile deleted."
                    + (removedRequests == 0 ? "" : " " + removedRequests + " related request(s) removed.")
                    + (removedNotifications == 0 ? "" : " " + removedNotifications
                    + " related notification(s) removed.")
                    + (saved ? " Saved." : " Changes remain in memory."));
        } catch (java.util.NoSuchElementException exception) {
            showProfileError(exception.getMessage());
        }
    }

    private void searchStudent() {
        String id = idField.getText().trim();
        if (id.isEmpty()) {
            showProfileError("Enter a student ID to search.");
            return;
        }

        studentManager.findStudentById(id).ifPresentOrElse(
                student -> {
                    refreshTable(List.of(student));
                    fillForm(student);
                    showProfileStatus("Student profile found.");
                },
                () -> {
                    refreshTable(List.of());
                    selectedStudentId = null;
                    showProfileStatus("No student found with ID " + id + ".");
                });
    }

    private void showAllStudents() {
        selectedStudentId = null;
        refreshTable(studentManager.getAllStudents());
        showProfileStatus(studentManager.getAllStudents().size() + " student profile(s).");
    }

    private void loadSelectedStudent() {
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow < 0) {
            selectedStudentId = null;
            return;
        }

        String id = tableModel.getValueAt(selectedRow, 0).toString();
        studentManager.findStudentById(id).ifPresent(this::fillForm);
    }

    private void fillForm(Student student) {
        selectedStudentId = student.getId();
        idField.setText(student.getId());
        nameField.setText(student.getName());
        emailField.setText(student.getEmail());
        departmentField.setText(student.getDepartment());
        yearField.setText(Integer.toString(student.getYearOfStudy()));
    }

    private Student readStudentFromForm() {
        String yearText = yearField.getText().trim();
        if (yearText.isEmpty()) {
            throw new IllegalArgumentException("Year of study is required.");
        }

        int yearOfStudy;
        try {
            yearOfStudy = Integer.parseInt(yearText);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Year of study must be a whole number.");
        }

        return new Student(
                idField.getText(),
                nameField.getText(),
                emailField.getText(),
                departmentField.getText(),
                yearOfStudy);
    }

    private void addSkill() {
        Student student = findSkillStudent();
        if (student == null) {
            return;
        }

        try {
            Skill skill = new Skill(skillNameField.getText(), categoryField.getText());
            if (skillTypeCombo.getSelectedIndex() == 0) {
                student.addSkillToTeach(skill);
            } else {
                student.addSkillToLearn(skill);
            }
            updateSessionSkillChoices();
            showStudentSkills(student);
            refreshPortfolioView();
            boolean saved = saveData();
            showSkillStatus("Skill added to " + student.getName() + "'s profile."
                    + (saved ? " Saved." : " Changes remain in memory."));
        } catch (IllegalArgumentException exception) {
            showSkillError(exception.getMessage());
        }
    }

    private void searchSkillOffers() {
        try {
            List<SkillMatcher.SkillSearchResult> results = skillMatcher.searchSkills(
                    studentManager.getAllStudents(),
                    skillNameField.getText(),
                    categoryField.getText());
            skillTableModel.setRowCount(0);
            for (SkillMatcher.SkillSearchResult result : results) {
                addSkillTableRow(result.student(), result.matchingSkills(), "—");
            }
            showSkillStatus(results.size() + " student profile(s) offer matching skills.");
        } catch (IllegalArgumentException exception) {
            showSkillError(exception.getMessage());
        }
    }

    private void findSkillMatches() {
        Student learner = findSkillStudent();
        if (learner == null) {
            return;
        }
        if (learner.getSkillsToLearn().isEmpty()) {
            showSkillError("This student has not added any skills they want to learn.");
            return;
        }

        List<SkillMatcher.MatchResult> results = skillMatcher.findMatches(
                learner,
                studentManager.getAllStudents(),
                skillNameField.getText(),
                categoryField.getText());
        skillTableModel.setRowCount(0);
        for (SkillMatcher.MatchResult result : results) {
            addSkillTableRow(
                    result.student(),
                    result.matchingSkills(),
                    String.format(Locale.ROOT, "%.1f%%", result.matchPercentage()));
        }
        showSkillStatus(results.size() + " student match(es) found for " + learner.getName() + ".");
    }

    private void sendExchangeRequest() {
        try {
            ExchangeRequest request = exchangeRequestManager.createRequest(
                    requesterIdField.getText(),
                    recipientIdField.getText(),
                    offeredSkillField.getText(),
                    requestedSkillField.getText());
            createNotificationIfAbsent(request.getRecipient().getId(), "EXCHANGE_REQUEST",
                    "You received exchange request #" + request.getRequestId() + " from "
                            + request.getRequester().getName() + ".");
            refreshRequestTable();
            boolean saved = saveData();
            refreshNotificationsAfterOperation();
            showExchangeStatus("Request #" + request.getRequestId() + " sent as Pending."
                    + (saved ? " Saved." : " Changes remain in memory."));
        } catch (IllegalArgumentException exception) {
            showExchangeError(exception.getMessage());
        }
    }

    private void respondToSelectedRequest(boolean accept) {
        int selectedRow = requestTable.getSelectedRow();
        if (selectedRow < 0) {
            showExchangeError("Select an exchange request first.");
            return;
        }

        int requestId = Integer.parseInt(requestTableModel.getValueAt(selectedRow, 0).toString());
        try {
            ExchangeRequest request = exchangeRequestManager.getAllRequests().stream()
                    .filter(item -> item.getRequestId() == requestId)
                    .findFirst()
                    .orElseThrow(() -> new java.util.NoSuchElementException(
                            "Exchange request was not found."));
            if (accept) {
                exchangeRequestManager.acceptRequest(requestId, respondingRecipientIdField.getText());
            } else {
                exchangeRequestManager.rejectRequest(requestId, respondingRecipientIdField.getText());
            }
            String status = accept ? "accepted" : "rejected";
            createNotificationIfAbsent(request.getRequester().getId(),
                    accept ? "EXCHANGE_ACCEPTED" : "EXCHANGE_REJECTED",
                    "Your exchange request #" + requestId + " was " + status + ".");
            refreshRequestTable();
            boolean saved = saveData();
            refreshNotificationsAfterOperation();
            showExchangeStatus("Request #" + requestId + (accept ? " accepted." : " rejected.")
                    + (saved ? " Saved." : " Changes remain in memory."));
        } catch (IllegalArgumentException | IllegalStateException
                 | java.util.NoSuchElementException exception) {
            showExchangeError(exception.getMessage());
        }
    }

    private void refreshRequestTable() {
        requestTableModel.setRowCount(0);
        for (ExchangeRequest request : exchangeRequestManager.getAllRequests()) {
            requestTableModel.addRow(new Object[]{
                    request.getRequestId(),
                    request.getRequester().getId(),
                    request.getRequester().getName(),
                    request.getRecipient().getId(),
                    request.getRecipient().getName(),
                    request.getOfferedSkill().getName(),
                    request.getRequestedSkill().getName(),
                    formatStatus(request.getStatus())
            });
        }
    }

    private String formatStatus(ExchangeRequest.Status status) {
        return switch (status) {
            case PENDING -> "Pending";
            case ACCEPTED -> "Accepted";
            case REJECTED -> "Rejected";
        };
    }

    private void showExchangeError(String message) {
        JOptionPane.showMessageDialog(this, message, "Skill Exchange", JOptionPane.WARNING_MESSAGE);
        showExchangeStatus(message);
    }

    private void showExchangeStatus(String message) {
        exchangeStatusLabel.setText(message);
    }

    private Student findSkillStudent() {
        String id = skillStudentIdField.getText().trim();
        if (id.isEmpty()) {
            showSkillError("Enter a student ID.");
            return null;
        }
        Student student = studentManager.findStudentById(id).orElse(null);
        if (student == null) {
            showSkillError("No student profile found with ID " + id + ".");
        }
        return student;
    }

    private void showStudentSkills(Student student) {
        skillTableModel.setRowCount(0);
        addSkillTableRow(student, List.of(), "—");
    }

    private void addSkillTableRow(Student student, List<Skill> matchingSkills, String matchPercentage) {
        skillTableModel.addRow(new Object[]{
                student.getId(),
                student.getName(),
                student.getEmail(),
                student.getDepartment(),
                student.getYearOfStudy(),
                formatSkills(student.getSkillsToTeach()),
                formatSkills(student.getSkillsToLearn()),
                formatSkills(matchingSkills),
                matchPercentage
        });
    }

    private String formatSkills(List<Skill> skills) {
        return skills.stream()
                .map(skill -> skill.getName() + " [" + skill.getCategory() + "]")
                .collect(Collectors.joining(", "));
    }

    private void refreshTable(List<Student> students) {
        selectedStudentId = null;
        tableModel.setRowCount(0);
        for (Student student : students) {
            tableModel.addRow(new Object[]{
                    student.getId(),
                    student.getName(),
                    student.getEmail(),
                    student.getDepartment(),
                    student.getYearOfStudy()
            });
        }
    }

    private void clearForm() {
        selectedStudentId = null;
        studentTable.clearSelection();
        idField.setText("");
        nameField.setText("");
        emailField.setText("");
        departmentField.setText("");
        yearField.setText("");
    }

    private void clearSkillFields() {
        skillStudentIdField.setText("");
        skillNameField.setText("");
        categoryField.setText("");
        skillTableModel.setRowCount(0);
        showSkillStatus("Skill fields cleared.");
    }

    private void showProfileError(String message) {
        JOptionPane.showMessageDialog(this, message, "Invalid Input", JOptionPane.WARNING_MESSAGE);
        showProfileStatus(message);
    }

    private void showProfileStatus(String message) {
        profileStatusLabel.setText(message);
    }

    private void showSkillError(String message) {
        JOptionPane.showMessageDialog(this, message, "Skill Discovery", JOptionPane.WARNING_MESSAGE);
        showSkillStatus(message);
    }

    private void showSkillStatus(String message) {
        skillStatusLabel.setText(message);
    }

    private record NotificationRecipient(String studentId, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    private record RoadmapStudentChoice(String studentId, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    private record CompletedSessionChoice(Session session) {
        @Override
        public String toString() {
            return "Session #" + session.getSessionId() + " — " + session.getSkill().getName()
                    + " on " + session.getDate() + " at " + session.getStartTime();
        }
    }

    private record StudentChoice(Student student) {
        @Override
        public String toString() {
            return student.getName() + " (" + student.getId() + ")";
        }
    }
}
