import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import com.fazecast.jSerialComm.SerialPort;


public class MainWindow {
    public static final String MISSING = "<MISSING!>";
    private final Dimension DEFAULT_BUTTON_SIZE = new Dimension(100, 40);
    private final JTextArea logArea = new JTextArea();
    private final ArrayList<HexLine> hexProgramCommands = new ArrayList<>();
    private final JFrame frame = new JFrame();
    private SerialPort port;
    private final DeviceConfiguration deviceConfiguration = new DeviceConfiguration();

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
                log("Opening file: " + fc.getSelectedFile().getName());
                chosenFileLabel.setText("Selected: " + fc.getSelectedFile().getName());
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
        sendButton.addActionListener(e -> {
            if (port == null) {
                showErrorNoPort();
                return;
            }
            startProgramming();
        });
        sendButton.setSize(DEFAULT_BUTTON_SIZE);
        sendPanel.add(sendButton);
        mainPanel.add(sendPanel);

        setUpLogArea(mainPanel);

        frame.setVisible(true);
    }

    private void startProgramming() {
        if (hexProgramCommands.isEmpty()) {
            showError("Please select proper hex file first.");
            return;
        }
        if (deviceConfiguration.DeviceID.isBlank()) {
            showError("Hex file does not contain Device ID. Proceeding without this check.");
        } else {
            showError("Device ID is written in HEX but id check is not implemented. Proceed with caution!");
            //Device ID check here
        }
        if(!testArduino()){
            showError("Cannot connect to arduino. Please ensure proper connection and select correct serial port.");
            return;
        }
        JDialog progressDialog = new JDialog(frame, "Programming");
        progressDialog.setBounds(frame.getX() + frame.getWidth() / 2 - 150, frame.getY() + frame.getHeight() / 2 - 10, 0, 0);
        progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JProgressBar progressBar = new JProgressBar(0, hexProgramCommands.size());
        progressBar.setPreferredSize(new Dimension(300, 20));
        progressBar.setStringPainted(true);
        progressDialog.add(progressBar);
        progressDialog.pack();
        progressDialog.setVisible(true);
        boolean programmingSuccess = true;
        Thread programmingThread = new Thread() {
            @Override
            public void run() {
                super.run();
                frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                for (int i = 0; i < hexProgramCommands.size(); i++) {
                    HexLine line = hexProgramCommands.get(i);
                    progressBar.setValue(i + 1);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (!sendLine(line)) {
                        break;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (!verifyResponse(line)) {
                        break;
                    }
                    progressBar.setValue(i);
                }
                if(progressBar.getValue()==progressBar.getMaximum()){
                    JOptionPane.showMessageDialog(frame, "Programming complete.",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                }else{
                    JOptionPane.showMessageDialog(frame, "Programming interrupted.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                progressDialog.dispose();

            }
        };
        programmingThread.start();

    }

    private boolean verifyResponse(HexLine line) {
        int expectedResponseLength = line.getDataAndChecksum().length()/2;
        if("01".equals(line.getType())){
            if(verifyDoneResponse()){
                log("Response correct\nProgramming completed without errors.");
                return true;
            } else{
                log("Response not correct but programming completed with no errors.");
                return false;
            }
        }
        byte[] result = new byte[expectedResponseLength];
        if(expectedResponseLength<5){
            result = new byte[5];
        }
        int bytesToRead = Math.max(expectedResponseLength, 5);
        int actualResponseLength = ReadFromPort(result, bytesToRead, true);
        if(expectedResponseLength != actualResponseLength){
            handleErrorFromArduino();
            return false;
        }

        byte[] expectedByteArray = line.parseForSend();
        boolean responseCorrect = true;
        for (int i = 0; i < expectedResponseLength; i++) {
            if(result[i]!=expectedByteArray[i+5]){
                responseCorrect=false;
                break;
            }
        }
        if (!responseCorrect) {
            String error = "Something went wrong!";
            error+="\n Data mismatch!";
            error+="\n Data sent to arduino : " + line.getDataAndChecksum();
            error+="\n Response from arduino: " + Arrays.toString(result);
            error+="\n Those should be equals!";
            error+="\n Full command sent to arduino: " + line;

            showError(error);
            return false;
        }
        log("Response correct.\n");
        return true;
    }

    private void handleErrorFromArduino() {
        WriteToPort(new byte[]{'k'},1,false);
        byte[] result = new byte[1];
        ReadFromPort(result,1,false);
        int responseLength = result[0];
        result = new byte[responseLength];
        ReadFromPort(result, responseLength,false);
        showError("\n Arduino responded with error: "+ new String(result,StandardCharsets.UTF_8));
    }

    private boolean verifyDoneResponse() {
        byte[] result = new byte[5];
        ReadFromPort(result,5,true);
        String response = new String(result, StandardCharsets.UTF_8);
        return "Done!".equals(response);
    }

    private boolean sendLine(HexLine line) {
        log("Sending command: " + line.toString());
        byte[] command = line.parseForSend();
        int writeResult = WriteToPort(command, command.length, false);
        if (writeResult != command.length) {
            String error;
            if (writeResult <= 0) {
                error = "Cannot send any data to Arduino!";
            } else {
                error = "Tried to send " + command.length + " bytes of data but only " + writeResult + " has been sent";
            }
            showError(error);
            return false;
        } else{
            log("Send ok.");
        }
        return true;
    }

    private JPanel getJPanelWithComPorts() {
        log("Detecting serial ports.");
        SerialPort[] comPorts = SerialPort.getCommPorts();
        ArrayList<String> choices = new ArrayList<>();
        for (SerialPort port : comPorts) {
            choices.add(port.getDescriptivePortName());
        }
        if (choices.isEmpty()) {
            showError("No COM ports detected. Please connect Arduino and restart program");
        } else {
            log("Found " + choices.size() + " COM ports. Trying to open first one.");
            openPort(choices.getFirst());
        }
        comPortsComboBox = new JComboBox<>(choices.toArray());
        comPortsComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
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
        if (port == null) {
            showErrorNoPort();
            return;
        }
        WriteToPort(new byte[]{'i'}, 1, true);
        byte[] result = new byte[400];
        int numRead = ReadFromPort(result, result.length, true);
        if(numRead > 0){
            String s = new String(result, StandardCharsets.UTF_8);
            log("Result of testing PIC: " + s);
        }
    }

    private void showErrorNoPort() {
        showError("No COM port connected. Connect Arduino and restart program");
    }

    private void showError(String content) {
        JOptionPane.showMessageDialog(frame, content,
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    private boolean testArduino() {
        if (port == null) {
            showErrorNoPort();
            return false;
        }
        WriteToPort(new byte[]{(byte) 0x68}, 1, true);

        byte[] result = new byte[43];
        int numRead = ReadFromPort(result,result.length, true);
        if (numRead > 0){
            String s = new String(result, StandardCharsets.UTF_8);
            log("Response from serial port: " + s);
            if("Arduino PIC16(L)F1847 programmer by Kumdzio".equals(s)){
                log("That is correct response. Port and connection to Arduino ok.");
                return true;
            }else{
                log("That is not correct response. Please ensure proper connection and select correct port.");
                return false;
            }
        }
        return false;
    }

    private int ReadFromPort(byte[] result, int length, boolean logResult) {
        int numRead = port.readBytes(result, length);
        if(!logResult) return numRead;
        if (numRead <= 0) {
            log("No response! Please check port number and connection to Arduino.");
        } else {
            log("Got response of " + numRead + " bytes.");
        }
        return numRead;
    }

    private int WriteToPort(byte[] command, int numberOfBytesToWrite, boolean logResult) {
        int writeResult = port.writeBytes(command, numberOfBytesToWrite);
        if (!logResult) return writeResult;
        if (writeResult == -1) {
            log("Error occured when writing to selected serial port");
        } else {
            log("Successfully written " + writeResult + " bytes.");
        }
        return writeResult;
    }

    private void LoadFile(File file) {
        hexProgramCommands.clear();
        try {
            Scanner myReader = new Scanner(file);
            while (myReader.hasNextLine()) {
                hexProgramCommands.add(new HexLine(myReader.nextLine()));
            }
            //finding and fetching lines which may contain configuration
            ArrayList<HexLine> linesWithConfiguration = new ArrayList<>();
            for (int i = 0; i < hexProgramCommands.size(); i++) {
                if (!"04".equals(hexProgramCommands.get(i).getType()) && !"02".equals(hexProgramCommands.get(i).getType())) {
                    continue;
                }
                if (("04".equals(hexProgramCommands.get(i).getType()) && "0001F9".equals(hexProgramCommands.get(i).getDataAndChecksum()))
                        || "02".equals(hexProgramCommands.get(i).getType()) && "1000EC".equals(hexProgramCommands.get(i).getDataAndChecksum())) {
                    int j = 1;
                    while (Integer.parseInt(hexProgramCommands.get(i + j).getAddress(), 16) < 22) {
                        linesWithConfiguration.add(hexProgramCommands.get(i + j));
                        j++;
                    }
                    break;
                }
            }
            //extracting values from found lines
            for (HexLine line : linesWithConfiguration) {
                for (int i = 0; i < Integer.parseInt(line.getSize(), 16); i++) {
                    switch (Integer.parseInt(line.getAddress(), 16) + i) {
                        case 0:
                            deviceConfiguration.UserID1 = deviceConfiguration.flipBytes(line.getDataAndChecksum().substring(i * 2, 4 + i * 2));
                            break;
                        case 2:
                            deviceConfiguration.UserID2 = deviceConfiguration.flipBytes(line.getDataAndChecksum().substring(i * 2, 4 + i * 2));
                            break;
                        case 4:
                            deviceConfiguration.UserID3 = deviceConfiguration.flipBytes(line.getDataAndChecksum().substring(i * 2, 4 + i * 2));
                            break;
                        case 6:
                            deviceConfiguration.UserID4 = deviceConfiguration.flipBytes(line.getDataAndChecksum().substring(i * 2, 4 + i * 2));
                            break;
                        case 12:
                            deviceConfiguration.DeviceID = deviceConfiguration.flipBytes(line.getDataAndChecksum().substring(i * 2, 4 + i * 2));
                            break;
                        case 14:
                            deviceConfiguration.Configuration1 = deviceConfiguration.flipBytes(line.getDataAndChecksum().substring(i * 2, 4 + i * 2));
                            break;
                        case 16:
                            deviceConfiguration.Configuration2 = deviceConfiguration.flipBytes(line.getDataAndChecksum().substring(i * 2, 4 + i * 2));
                            break;
                    }
                }
            }
            String content = "Configuration bytes from hex file: \n";
            content += "UserID1: 0x" + (deviceConfiguration.UserID1.isBlank() ? MISSING : deviceConfiguration.UserID1) + "\n";
            content += "UserID2: 0x" + (deviceConfiguration.UserID2.isBlank() ? MISSING : deviceConfiguration.UserID2) + "\n";
            content += "UserID3: 0x" + (deviceConfiguration.UserID3.isBlank() ? MISSING : deviceConfiguration.UserID3) + "\n";
            content += "UserID4: 0x" + (deviceConfiguration.UserID4.isBlank() ? MISSING : deviceConfiguration.UserID4) + "\n";
            content += "DeviceID: 0x" + (deviceConfiguration.DeviceID.isBlank() ? MISSING : deviceConfiguration.DeviceID) + "\n";
            content += "Configuration1: 0x" + (deviceConfiguration.Configuration1.isBlank() ? MISSING : deviceConfiguration.Configuration1) + "\n";
            content += "Configuration2: 0x" + (deviceConfiguration.Configuration2.isBlank() ? MISSING : deviceConfiguration.Configuration2) + "\n";
            if (!deviceConfiguration.isComplete()) {
                content += "\n WARNING: hex file does not contain full configuration or target device ID.\nIt may lead to unstable or not working device.";
            }
            JOptionPane.showMessageDialog(frame, content,
                    "Error", JOptionPane.INFORMATION_MESSAGE);
        } catch (FileNotFoundException ex) {
            hexProgramCommands.clear();
            showError("File is not existing or do not have access to it.");
        }
        log("Hex file opened and loaded successfully.");
    }

    private void setUpLogArea(JPanel parent) {
        JPanel labelPanel = new JPanel();
        labelPanel.setSize(parent.getSize().width, 20);
        BoxLayout labelPanelLayout = new BoxLayout(labelPanel, BoxLayout.LINE_AXIS);
        labelPanelLayout.minimumLayoutSize(labelPanel);
        labelPanel.setLayout(labelPanelLayout);
        labelPanel.add(new JLabel("Application log: "));
        parent.add(labelPanel);

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setWheelScrollingEnabled(true);
        logScrollPane.setVerticalScrollBar(logScrollPane.createVerticalScrollBar());
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        logScrollPane.setPreferredSize(new Dimension(480, 300));
        parent.add(logScrollPane);
    }

    private void log(String text) {
        logArea.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
        logArea.append(": ");
        logArea.append(text);
        logArea.append("\n");
        logArea.setCaretPosition(logArea.getText().length());
    }

    private void openPort(String selected) {
        int startIndex = selected.lastIndexOf("(") + 1;
        int endIndex = selected.lastIndexOf(")");
        String com = selected.substring(startIndex, endIndex);
        log("Opening com port: " + com);
        if (port != null) {
            port.closePort();
        }
        port = SerialPort.getCommPort(com);
        port.setBaudRate(57600);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING|SerialPort.TIMEOUT_WRITE_BLOCKING, 100, 100);
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