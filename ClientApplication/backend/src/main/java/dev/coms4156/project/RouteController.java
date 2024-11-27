package dev.coms4156.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.coms4156.project.command.*;
import dev.coms4156.project.exception.NotFoundException;
import dev.coms4156.project.utils.CodecUtils;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * This class contains all the API routes for the system.
 */
@RestController
public class RouteController {

  @Autowired
  private MyServiceCaller serviceCaller;

  /**
   * Redirects to the homepage.
   */
  @GetMapping({"/", "/index", "/home"})
  public String index() {
    return "Welcome, in order to make an API call direct your browser or Postman to an endpoint.";
  }

  /**
   * Login by validating employee ID and name.
   *
   * @param employeeId the employee ID
   * @param name the employee name
   * @return a success message if the credentials are valid, or an error message if invalid
   */
  @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> login(
    @RequestParam("eid") String employeeId,
    @RequestParam("name") String name
  ) {
    Map<String, String> response = new HashMap<>();
    try {
      // Call the service to get employee info
      String employeeInfo = serviceCaller.getEmployeeInfo(employeeId);

      if (employeeInfo == null || employeeInfo.isEmpty()) {
        response.put("status", "failed");
        response.put("message", "Employee ID does not exist");
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
      }

      // Parse the response to get the employee name (assuming JSON response)
      ObjectMapper objectMapper = new ObjectMapper();
      Map<String, Object> employeeData = objectMapper.readValue(employeeInfo, Map.class);
      String fetchedName = (String) employeeData.get("name");
      System.out.println(employeeData);
      System.out.println(name);
      System.out.println(fetchedName);

      // Validate the name
      if (!name.equals(fetchedName)) {
        response.put("status", "failed");
        response.put("message", "Employee name does not match");
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
      }

      // Login successful
      response.put("status", "success");
      response.put("message", "Logged in as " + fetchedName);
      return new ResponseEntity<>(response, HttpStatus.OK);

    } catch (Exception e) {
      e.printStackTrace();
      response.put("status", "failed");
      response.put("message", "An error occurred while logging in");
      return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Registers a new employee within the specified organization.
   * This function allows for adding a new employee with details such as
   * full name, hire date, position, and department ID to an existing organization.
   *
   * @param firstName the first name of the employee
   * @param lastName the last name of the employee
   * @param departmentId the ID of the department where the employee will be added
   * @param hireDate the hire date of the employee in "yyyy-MM-dd" format
   * @param position the position or role of the employee within the department
   * @return a success message along with the employee ID if the registration is successful,
   *         or an error message if the operation fails
   */
  @PostMapping("/register")
  public ResponseEntity<?> registerEmployee(
    @RequestParam("firstName") String firstName,
    @RequestParam("lastName") String lastName,
    @RequestParam("departmentId") int departmentId,
    @RequestParam("hireDate") String hireDate,
    @RequestParam("position") String position
  ) {
    Map<String, Object> response = new HashMap<>();

    try {
      // Combine first and last name
      String fullName = firstName + " " + lastName;

      // Call service to add a new employee
      String addEmployeeResponse = serviceCaller.registerNewEmployee(fullName, departmentId, hireDate, position);
      ObjectMapper objectMapper = new ObjectMapper();
      Map<String, Object> employeeData = objectMapper.readValue(addEmployeeResponse, Map.class);
      System.out.println(employeeData);

      if ((int) employeeData.get("status") != 200) {
        response.put("status", "failed");
        response.put("message", "Failed to register new employee");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
      }

      response.put("status", "success");
      response.put("message", employeeData.get("message"));

      return new ResponseEntity<>(response, HttpStatus.CREATED);
    } catch (Exception e) {
      e.printStackTrace();
      response.put("status", "failed");
      response.put("message", "An error occurred while registering the employee");
      return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Retrieves organization information for the given client ID.
   *
   * @return the response from the service containing organization information
   */
  @GetMapping(value = "/getOrgInfo", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> getOrganization(
    @RequestParam("cid") String clientId
  ) {
    try {
      // Call the service to retrieve organization info
      String response = serviceCaller.getOrganizationInfo(clientId);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      // Handle any errors and return an appropriate response
      Map<String, String> errorResponse = new HashMap<>();
      errorResponse.put("status", "failed");
      errorResponse.put("message", e.getMessage());
      return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
  }


  /**
   * Adds a recurring shift assignment for an employee.
   *
   * @param clientId the encoded client ID
   * @param employeeId the employee ID
   * @param dayOfWeek the day of week (1-7, Monday to Sunday)
   * @param timeSlot the time slot (0: 9-12, 1: 14-17, 2: 18-21)
   * @return a success message if the shift is assigned, or an error message if the operation fails
   */
  @PostMapping(value = "/addShift", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> addShift(
          @RequestParam("cid") String clientId,
          @RequestParam("employeeId") int employeeId,
          @RequestParam("dayOfWeek") int dayOfWeek,
          @RequestParam("timeSlot") int timeSlot
  ) {
    try {
      int cid = Integer.parseInt(CodecUtils.decode(clientId));
      Command command = new AddShiftCmd(cid, employeeId,
              DayOfWeek.of(dayOfWeek), TimeSlot.fromValue(timeSlot));
      Map<String, Object> response = (Map<String, Object>) command.execute();

      if (response.get("status").equals("success")) {
        return new ResponseEntity<>(response, HttpStatus.CREATED);
      } else {
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
      }
    } catch (NotFoundException | IllegalArgumentException e) {
      Map<String, String> response = new HashMap<>();
      response.put("status", "failed");
      response.put("message", e.getMessage());
      return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * Gets shift assignments of an organization.
   *
   * @param clientId the encoded client ID
   * @return a list of shift assignments
   */
  @GetMapping(value = "/getShift", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> getShift(
          @RequestParam("cid") String clientId
  ) {
    try {
      int cid = Integer.parseInt(CodecUtils.decode(clientId));
      Command command = new GetShiftCmd(cid);
      Map<String, Object> response = (Map<String, Object>) command.execute();
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (NotFoundException | IllegalArgumentException e) {
      Map<String, String> response = new HashMap<>();
      response.put("status", "failed");
      response.put("message", e.getMessage());
      return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * Removes a shift assignment.
   *
   * @param clientId the encoded client ID
   * @param employeeId the employee ID
   * @param dayOfWeek the day of week (1-7, Monday to Sunday)
   * @param timeSlot the time slot (0: 9-12, 1: 14-17, 2: 18-21)
   * @return a success message if the shift is removed, or an error message if the operation fails
   */
  @DeleteMapping(value = "/removeShift", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> removeShift(
          @RequestParam("cid") String clientId,
          @RequestParam("employeeId") int employeeId,
          @RequestParam("dayOfWeek") int dayOfWeek,
          @RequestParam("timeSlot") int timeSlot
  ) {
    try {
      int cid = Integer.parseInt(CodecUtils.decode(clientId));
      Command command = new RemoveShiftCmd(cid, employeeId,
              DayOfWeek.of(dayOfWeek), TimeSlot.fromValue(timeSlot));
      Map<String, Object> response = (Map<String, Object>) command.execute();

      if (response.get("status").equals("success")) {
        return new ResponseEntity<>(response, HttpStatus.OK);
      } else {
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
      }
    } catch (NotFoundException | IllegalArgumentException e) {
      Map<String, String> response = new HashMap<>();
      response.put("status", "failed");
      response.put("message", e.getMessage());
      return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * Handles any exceptions that occur in controller.
   *
   * @param e the exception that occurred
   * @return the response entity
   * @deprecated This method is replaced by the global exception handler after 80586a8
   */
  @Deprecated
  private ResponseEntity<?> handleException(Exception e) {
    System.out.println(e.toString());
    return new ResponseEntity<>("An Error has occurred", HttpStatus.INTERNAL_SERVER_ERROR);
  }

}