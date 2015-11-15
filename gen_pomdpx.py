from lxml import etree

ns = 'http://www.w3.org/2001/XMLSchema-instance'
root_pomdpx = etree.Element('pomdpx', nsmap = {'xsi': ns}, attrib = {'version': '0.1', 'id': 'ROHC', '{{{0}}}noNamespaceSchemaLocation'.format(ns): 'pomdpx.xsd'});

description = etree.SubElement(root_pomdpx, 'Description')
discount = etree.SubElement(root_pomdpx, 'Discount')
variable = etree.SubElement(root_pomdpx, 'Variable')
initial_state_belief = etree.SubElement(root_pomdpx, 'InitialStateBelief')
state_transition_function = etree.SubElement(root_pomdpx, 'StateTransitionFunction')
obs_function = etree.SubElement(root_pomdpx, 'ObsFunction')
reward_function = etree.SubElement(root_pomdpx, 'RewardFunction')

tree_pomdpx = etree.ElementTree(root_pomdpx)
with open('instance.pomdpx', 'wb') as output_file:
    tree_pomdpx.write(output_file, pretty_print=True, encoding="iso-8859-1")
