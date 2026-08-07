#!/usr/bin/env python3
"""Fix incorrectly quoted t() calls in ChatbotStudioView.tsx"""

FILE = 'src/pages/aiworkbench/ChatbotStudioView.tsx'

with open(FILE, 'r') as f:
    content = f.read()

# Pattern: 't('aiworkbench.chatbot.XXX')' inside object properties/JSX
# These need the outer quotes removed
import re

# Fix 1: Object properties with t() wrapped in quotes
# type: 't(...)' → type: t(...)
fixes = [
    # Line 90: type: 't('aiworkbench.chatbot.docTypePDF')'  
    ("type: 't('aiworkbench.chatbot.docTypePDF')'", "type: t('aiworkbench.chatbot.docTypePDF')"),
    # Line 92: type: 't('aiworkbench.chatbot.docTypeText')'
    ("type: 't('aiworkbench.chatbot.docTypeText')'", "type: t('aiworkbench.chatbot.docTypeText')"),
    
    # Line 327: actionName: 't('aiworkbench.chatbot.actionReschedule')'
    ("actionName: 't('aiworkbench.chatbot.actionReschedule')'", "actionName: t('aiworkbench.chatbot.actionReschedule')"),
    
    # Line 767: label: 't(...)', desc: 't(...)'
    ("label: 't('aiworkbench.chatbot.objFlight')', desc: 't('aiworkbench.chatbot.objFlightDesc')'",
     "label: t('aiworkbench.chatbot.objFlight'), desc: t('aiworkbench.chatbot.objFlightDesc')"),
    ("label: 't('aiworkbench.chatbot.objPilot')', desc: 't('aiworkbench.chatbot.objPilotDesc')'",
     "label: t('aiworkbench.chatbot.objPilot'), desc: t('aiworkbench.chatbot.objPilotDesc')"),
    ("label: 't('aiworkbench.chatbot.objAirport')', desc: 't('aiworkbench.chatbot.objAirportDesc')'",
     "label: t('aiworkbench.chatbot.objAirport'), desc: t('aiworkbench.chatbot.objAirportDesc')"),
    ("label: 't('aiworkbench.chatbot.objEquipment')', desc: 't('aiworkbench.chatbot.objEquipmentDesc')'",
     "label: t('aiworkbench.chatbot.objEquipment'), desc: t('aiworkbench.chatbot.objEquipmentDesc')"),
    
    # Line 809: name: 't(...)', desc: 't(...)'
    ("name: 't('aiworkbench.chatbot.actionReschedule')', desc: 't('aiworkbench.chatbot.actionRescheduleDesc')'",
     "name: t('aiworkbench.chatbot.actionReschedule'), desc: t('aiworkbench.chatbot.actionRescheduleDesc')"),
    ("name: 't('aiworkbench.chatbot.actionAssignPilot')', desc: 't('aiworkbench.chatbot.actionAssignPilotDesc')'",
     "name: t('aiworkbench.chatbot.actionAssignPilot'), desc: t('aiworkbench.chatbot.actionAssignPilotDesc')"),
    
    # Line 915: ternary with t() in quotes
    ("${doc.chunksCount > 0 ? `${doc.chunksCount} chunks` : 't('aiworkbench.chatbot.pendingExtract')'}`",
     "${doc.chunksCount > 0 ? `${doc.chunksCount} chunks` : t('aiworkbench.chatbot.pendingExtract')}`"),
    
    # Line 1025: ternary with t() in quotes  
    ("gr.severity === 'block' ? 't('aiworkbench.chatbot.severityBlock')' : gr.severity === 'warn' ? 't('aiworkbench.chatbot.severityWarn')' : 't('aiworkbench.chatbot.severityAudit')'",
     "gr.severity === 'block' ? t('aiworkbench.chatbot.severityBlock') : gr.severity === 'warn' ? t('aiworkbench.chatbot.severityWarn') : t('aiworkbench.chatbot.severityAudit')"),
    
    # Lines 1397-1398: ternary with t() in quotes
    ("msg.actionProposal.status === 'approved' ? 't('aiworkbench.chatbot.proposalApproved')' :",
     "msg.actionProposal.status === 'approved' ? t('aiworkbench.chatbot.proposalApproved') :"),
    ("msg.actionProposal.status === 'rejected' ? 't('aiworkbench.chatbot.proposalRejected')' : 't('aiworkbench.chatbot.proposalPending')'",
     "msg.actionProposal.status === 'rejected' ? t('aiworkbench.chatbot.proposalRejected') : t('aiworkbench.chatbot.proposalPending')"),
    
    # Line 915 also has: <span>t('aiworkbench.chatbot.docChunks')
    # This is JSX text so it should be {t(...)} format
    ("<span>t('aiworkbench.chatbot.docChunks')", "<span>{t('aiworkbench.chatbot.docChunks')}"),
    ("<span>{t('aiworkbench.chatbot.docSize')}", "<span>{t('aiworkbench.chatbot.docSize')}"),  
]

for old, new in fixes:
    if old in content:
        content = content.replace(old, new)
        print(f"Fixed: {old[:60]}...")
    else:
        print(f"NOT FOUND: {old[:60]}...")

# Also check for any remaining quoted t() patterns
remaining = re.findall(r"'t\('aiworkbench\.chatbot\.[^']+'\)'", content)
if remaining:
    print(f"\nWARNING: {len(remaining)} remaining quoted t() calls:")
    for r in remaining:
        print(f"  {r}")
else:
    print("\n✓ No remaining quoted t() calls")

with open(FILE, 'w') as f:
    f.write(content)

print("DONE")
