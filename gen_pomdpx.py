#!/usr/bin/env python3
from lxml import etree

# Problem definition
# [1] Hermenier, Romain, Francesco Rossetto, and Matteo Berioli. "On the Behavior of RObust Header Compression U-mode in Channels with Memory." Wireless Communications, IEEE Transactions on 12.8 (2013): 3722-3732.
# [2] 

W = 29 # Capability of the WLSB coding
L_B = 5 # Average duration of a sequence of consecutive bad states
EPS = 0.002 # Average deletion probability

P_FA = 0.001 # False alarm probability
P_MD = 0.01 # Miss detection probability

GAMMA = 0.95 # The discount factor

# Initialization
P_BG = 1 / L_B 
P_GB = P_BG / (1 / EPS - 1)
P_BB, P_GG = 1 - P_BG, 1 - P_GB

# Outline of the pomdpx file
ns = 'http://www.w3.org/2001/XMLSchema-instance'
root_pomdpx = etree.Element('pomdpx', nsmap = {'xsi': ns}, attrib = {'version': '0.1', 'id': 'ROHC', '{{{0}}}noNamespaceSchemaLocation'.format(ns): 'pomdpx.xsd'});

description = etree.SubElement(root_pomdpx, 'Description')
discount = etree.SubElement(root_pomdpx, 'Discount')
variable = etree.SubElement(root_pomdpx, 'Variable')
initial_state_belief = etree.SubElement(root_pomdpx, 'InitialStateBelief')
state_transition_function = etree.SubElement(root_pomdpx, 'StateTransitionFunction')
obs_function = etree.SubElement(root_pomdpx, 'ObsFunction')
reward_function = etree.SubElement(root_pomdpx, 'RewardFunction')

# Description
description.text = "Cross-layer ROHC design problem. W = {0}; L_B = {1}, EPS = {2}; P_FA = {3}, P_MD = {4}; gamma = {5}.".format(W, L_B, EPS, P_FA, P_MD, GAMMA)

# Discount
discount.text = str(GAMMA)

# Variable
state_var = etree.SubElement(variable, 'StateVar', attrib = {'vnamePrev': 'state_0', 'vnameCurr': 'state_1'})
etree.SubElement(state_var, 'NumValues').text = str(4 + W) # The states are defined in the order of NC_B, NC_G, SC_B, SC_G, FC_0, FC_1, ..., FC_{W - 1}

obs_var = etree.SubElement(variable, 'ObsVar', attrib = {'vname': 'est_channel'})
etree.SubElement(obs_var, 'ValueEnum').text = "ogood obad"

action_var = etree.SubElement(variable, 'ActionVar', attrib = {'vname': 'type_compression'})
etree.SubElement(action_var, 'ValueEnum').text = "IR FO SO"

reward_var = etree.SubElement(variable, 'RewardVar', attrib = {'vname': 'efficiency'})

# Initial state belief
cp_isb = etree.SubElement(initial_state_belief, 'CondProb')
etree.SubElement(cp_isb, 'Var').text = "state_0"
etree.SubElement(cp_isb, 'Parent').text = "null"
param_cp_isb = etree.SubElement(cp_isb, 'Parameter', attrib = {'type': 'TML'})
entry_param_cp_isb = etree.SubElement(param_cp_isb, 'Entry')
etree.SubElement(entry_param_cp_isb, 'Instance').text = "-"
etree.SubElement(entry_param_cp_isb, 'ProbTable').text = ' '.join(list(map(str, [EPS, 1-EPS] + [0] * (2 + W))))

# State transition function

# Generate the .pomdpx file
tree_pomdpx = etree.ElementTree(root_pomdpx)
with open('instance.pomdpx', 'wb') as output_file:
    tree_pomdpx.write(output_file, pretty_print=True, encoding="iso-8859-1")
