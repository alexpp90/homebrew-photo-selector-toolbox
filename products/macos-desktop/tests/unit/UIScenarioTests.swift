import Foundation
import Testing
@testable import PhotoSelectorKit

@Suite("UIScenario Tests")
struct UIScenarioTests {
    @Test("Decodes and validates a valid scenario JSON")
    func testValidScenario() throws {
        let json = """
        {
          "id": "J-MAC-01",
          "window": {"width": 1440, "height": 900},
          "steps": [
            {"action": "waitLoaded"},
            {"action": "checkpoint", "name": "loaded"},
            {"action": "key", "key": "3"},
            {"action": "wait", "ms": 300},
            {"action": "click", "target": "toolbar_next"},
            {"action": "key", "key": "z", "modifiers": ["command"]},
            {"action": "checkpoint", "name": "after_undo"}
          ]
        }
        """
        let scenario = try JSONDecoder().decode(UIScenario.self, from: Data(json.utf8))
        try scenario.validate()
        #expect(scenario.id == "J-MAC-01")
        #expect(scenario.window == UIScenarioWindow(width: 1440, height: 900))
        #expect(scenario.steps.count == 7)
    }

    @Test("Rejects unknown action")
    func testUnknownAction() throws {
        let scenario = UIScenario(
            id: "bad",
            steps: [UIScenarioStep(action: "teleport")]
        )
        #expect(throws: UIScenarioValidationError.unknownAction(index: 0, action: "teleport")) {
            try scenario.validate()
        }
    }

    @Test("Rejects duplicate checkpoint names")
    func testDuplicateCheckpoints() throws {
        let scenario = UIScenario(
            id: "dup",
            steps: [
                UIScenarioStep(action: "checkpoint", name: "first"),
                UIScenarioStep(action: "checkpoint", name: "first")
            ]
        )
        #expect(throws: UIScenarioValidationError.duplicateCheckpoint(name: "first")) {
            try scenario.validate()
        }
    }

    @Test("Rejects missing required parameters")
    func testMissingParameters() throws {
        let missingKey = UIScenario(id: "s1", steps: [UIScenarioStep(action: "key")])
        #expect(throws: UIScenarioValidationError.missingParameter(index: 0, action: "key", parameter: "key")) {
            try missingKey.validate()
        }

        let missingClick = UIScenario(id: "s2", steps: [UIScenarioStep(action: "click")])
        #expect(throws: UIScenarioValidationError.missingParameter(index: 0, action: "click", parameter: "target")) {
            try missingClick.validate()
        }
    }
}
