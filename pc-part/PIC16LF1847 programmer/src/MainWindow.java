import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Scanner;

import com.fazecast.jSerialComm.SerialPort;


public class MainWindow {
    private final Dimension DEFAULT_BUTTON_SIZE = new Dimension(100, 40);
    private final JTextArea logArea = new JTextArea();
    private final ArrayList<String> hexProgramCommands = new ArrayList<>();
    private final JFrame frame = new JFrame();
    private SerialPort port;

    private JComboBox<Object> comPortsComboBox;
    public MainWindow() {
        frame.setTitle("PIC 16LF1847 programmer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setBounds(200, 200, 500, 500);
        frame.setLayout(new FlowLayout());
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
        mainPanel.setVisible(true);
        frame.add(mainPanel);

        JPanel hexFilePanel = new JPanel();
        hexFilePanel.setLayout(new FlowLayout());
        JButton openFileButton = new JButton("Load hex file");
        JLabel chosenFileLabel = new JLabel("File not chosen yet..");
        openFileButton.setSize(DEFAULT_BUTTON_SIZE);
        openFileButton.addActionListener(e -> {
            final JFileChooser fc = new JFileChooser();
            fc.setApproveButtonText("Open");
            fc.setApproveButtonToolTipText("Open selected file");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("Hex files", "hex");
            fc.setFileFilter(filter);
            int returnVal = fc.showOpenDialog(frame);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                log("Opening file: "+fc.getSelectedFile().getName());
                chosenFileLabel.setText("Selected: "+fc.getSelectedFile().getName());
                LoadFile(fc.getSelectedFile());
            } else {
               showError("Please select proper HEX file");
            }
        });
        hexFilePanel.add(openFileButton);
        hexFilePanel.add(chosenFileLabel);
        mainPanel.add(hexFilePanel);

        JPanel comPortPanel = getJPanelWithComPorts();
        mainPanel.add(comPortPanel);


        JPanel sendPanel = new JPanel();
        sendPanel.setLayout(new FlowLayout());
        JButton sendButton = new JButton("Start programming");
        sendButton.addActionListener( e -> {
            if(port == null){
                showErrorNoPort();
                return;
            }
        });
        sendButton.setSize(DEFAULT_BUTTON_SIZE);
        sendPanel.add(sendButton);
        mainPanel.add(sendPanel);

        setUpLogArea(mainPanel);

        frame.setVisible(true);
    }

    private JPanel getJPanelWithComPorts() {
        log("Detecting serial ports.");
        SerialPort[] comPorts = SerialPort.getCommPorts();
        ArrayList<String> choices = new ArrayList<>();
        for (SerialPort port : comPorts) {
            choices.add(port.getDescriptivePortName());
        }
        if(choices.isEmpty()){
            showError("No COM ports detected. Please connect Arduino and restart program");
        }else{
            openPort(choices.getFirst());
        }
        comPortsComboBox = new JComboBox<>(choices.toArray());
        comPortsComboBox.addItemListener(e -> {
            if(e.getStateChange()==ItemEvent.SELECTED){
                String selected = e.getItem().toString();
                openPort(selected);
            }
        });
        //comPortsComboBox.setSelectedIndex(0);
        JPanel comPortPanel = new JPanel();
        comPortPanel.setLayout(new FlowLayout());
        comPortsComboBox.setVisible(true);
        comPortPanel.add(comPortsComboBox);
        JButton testArduinoButton = new JButton("Test arduino");
        testArduinoButton.addActionListener(e -> {
            log("Start testing arduino at port: " + comPortsComboBox.getSelectedItem());
            testArduino();
        });
        comPortPanel.add(testArduinoButton);
        JButton testPicButton = new JButton("Test PIC");
        testPicButton.addActionListener(e -> {
            log("Start testing PIC...");
            testPic();
        });
        comPortPanel.add(testPicButton);
        return comPortPanel;
    }

    private void testPic() {
        if(port == null) {
            showErrorNoPort();
            return;
        }
        byte[] command = {'i'};
        int writeResult = port.writeBytes(command,1);
        log("Write result: "+writeResult);
        byte[] result = new byte[400];
        int numRead = port.readBytes(result, result.length);
        log("Read " + numRead + " bytes.");
        String s = new String(result, StandardCharsets.UTF_8);
        log("Result of testing PIC: "+s);
    }

    private void showErrorNoPort(){
        showError("No COM port connected. Connect Arduino and restart program");
    }
    private void showError(String content){
        JOptionPane.showMessageDialog(frame, content,
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void testArduino() {
        if(port == null) {
            showErrorNoPort();
            return;
        }
        byte[] command = {(byte)0x68};
        int writeResult = port.writeBytes(command,1);
        log("Write result: "+writeResult);
        byte[] result = new byte[43];
        int numRead = port.readBytes(result, result.length);
        log("Read " + numRead + " bytes.");
        String s = new String(result, StandardCharsets.UTF_8);
        log("Result of testing Arduino: "+s);
    }

    private void LoadFile(File file) {
        hexProgramCommands.clear();
        try {
            Scanner myReader = new Scanner(file);
            while (myReader.hasNextLine()) {
                hexProgramCommands.add(myReader.nextLine());
            }
        } catch (FileNotFoundException ex) {
            hexProgramCommands.clear();
            showError("File is not existing or do not have access to it.");
        }
        log("Hex file opened and loaded successfully.");
    }

    private void setUpLogArea(JPanel parent){
        JPanel labelPanel = new JPanel();
        labelPanel.setSize(parent.getSize().width,20);
        BoxLayout labelPanelLayout = new BoxLayout(labelPanel,BoxLayout.LINE_AXIS);
        labelPanelLayout.minimumLayoutSize(labelPanel);
        labelPanel.setLayout(labelPanelLayout);
        labelPanel.add(new JLabel("Application log: "));
        parent.add(labelPanel);

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setWheelScrollingEnabled(true);
        logScrollPane.setVerticalScrollBar(logScrollPane.createVerticalScrollBar());
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        logScrollPane.setPreferredSize(new Dimension(480,300));
        parent.add(logScrollPane);
    }

    private void log(String text){
        logArea.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
        logArea.append(": ");
        logArea.append(text);
        logArea.append("\n");
        logArea.setCaretPosition(logArea.getText().length());
    }

    private void openPort(String selected){
        int startIndex = selected.lastIndexOf("(")+1;
        int endIndex = selected.lastIndexOf(")");
        String com = selected.substring(startIndex,endIndex);
        log("Opening com port: "+com);
        if (port != null){
            port.closePort();
        }
        port = SerialPort.getCommPort(com);
        port.setBaudRate(57600);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 100, 0);
        port.openPort();
        try {
            Thread.sleep(400);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        port.writeBytes(new byte[]{'0'},1);
        try {
            Thread.sleep(400);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
}  